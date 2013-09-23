/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.shiena.seasar;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import javax.ejb.EJB;
import javax.transaction.TransactionManager;

import org.seasar.framework.container.S2Container;
import org.seasar.framework.container.factory.S2ContainerFactory;
import org.seasar.framework.container.factory.SingletonS2ContainerFactory;
import org.seasar.framework.container.impl.S2ContainerBehavior;
import org.seasar.framework.convention.NamingConvention;
import org.seasar.framework.convention.impl.NamingConventionImpl;
import org.seasar.framework.env.Env;
import org.seasar.framework.unit.InternalTestContext;
import org.seasar.framework.unit.S2TestIntrospector;
import org.seasar.framework.unit.Seasar2;
import org.seasar.framework.unit.UnitClassLoader;
import org.seasar.framework.unit.annotation.PublishedTestContext;
import org.seasar.framework.unit.impl.AnnotationTestIntrospector;
import org.seasar.framework.util.DisposableUtil;
import org.seasar.framework.util.ResourceUtil;
import org.seasar.framework.util.StringUtil;
import org.seasar.framework.util.tiger.CollectionsUtil;
import org.seasar.framework.util.tiger.ReflectionUtil;
import org.spockframework.runtime.extension.AbstractMethodInterceptor;
import org.spockframework.runtime.extension.IMethodInvocation;
import org.spockframework.runtime.model.FeatureInfo;
import org.spockframework.runtime.model.FieldInfo;

/**
 * S2TestMethodRunnerを元にしたインターセプターです。
 * @see org.seasar.framework.unit.S2TestMethodRunner
 *
 * @author Mitsuhiro Koga
 */
public class S2Interceptor extends AbstractMethodInterceptor {

    /** S2JUnit4のデフォルトの設定ファイルのパス */
    protected static final String DEFAULT_S2JUNIT4_PATH = "s2junit4.dicon";

    /** S2JUnit4の設定ファイルのパス */
    protected static String s2junit4Path = DEFAULT_S2JUNIT4_PATH;

    /** テストクラスのイントロスペクター */
    protected final S2TestIntrospector introspector;

    /** {@link #unitClassLoader テストで使用するクラスローダー}で置き換えられる前のオリジナルのクラスローダー */
    protected ClassLoader originalClassLoader;

    /** テストで使用するクラスローダー */
    protected UnitClassLoader unitClassLoader;

    /** S2JUnit4の内部的なテストコンテキスト */
    protected InternalTestContext testContext;

    /** バインディングが行われたフィールドのリスト */
    private List<Field> boundFields;

    /**
     * インスタンスを構築します。
     *
     */
    public S2Interceptor() {
        this.introspector = new AnnotationTestIntrospector();
    }

    @Override
    public void interceptSetupMethod(IMethodInvocation invocation) throws Throwable {
        Env.setFilePath(Seasar2.ENV_PATH);
        Env.setValueIfAbsent(Seasar2.ENV_VALUE);

        setUpTestContext(invocation);
        initContainer();

        invocation.proceed();
    }

    @Override
    public void interceptFeatureMethod(IMethodInvocation invocation) throws Throwable {
        bindFields(invocation);
        try {
            runTest(invocation);
        } finally {
            unbindFields(invocation);
        }
    }

    @Override
    public void interceptCleanupMethod(IMethodInvocation invocation) throws Throwable {
        try {
            invocation.proceed();
        } finally {
            if (testContext != null) {
                try {
                    testContext.destroyContainer();
                } finally {
                    tearDownTestContext();
                }
            }
        }
    }

    /**
     * テストコンテキストをセットアップします。
     *
     * @param invocation
     *            メソッド呼び出し
     * @throws Throwable
     *            何らかの例外またはエラーが起きた場合
     */
    protected void setUpTestContext(IMethodInvocation invocation) throws Throwable {
        FeatureInfo featureInfo = invocation.getFeature();
        Object test = invocation.getInstance();
        Class<?> testClass = test.getClass();
        Method method = featureInfo.getFeatureMethod().getReflection();

        boundFields = CollectionsUtil.newArrayList();
        originalClassLoader = getOriginalClassLoader();
        unitClassLoader = new UnitClassLoader(originalClassLoader);
        Thread.currentThread().setContextClassLoader(unitClassLoader);
        if (needsWarmDeploy(testClass, method)) {
            S2ContainerFactory.configure("warmdeploy.dicon");
        }
        final S2Container container = createRootContainer(testClass, method);
        SingletonS2ContainerFactory.setContainer(container);
        testContext = InternalTestContext.class.cast(container
                .getComponent(InternalTestContext.class));
        testContext.setTestClass(testClass);
        testContext.setTestMethod(method);
        if (!testContext.hasComponentDef(NamingConvention.class)
                && introspector.isRegisterNamingConvention(testClass, method)) {
            final NamingConvention namingConvention = new NamingConventionImpl();
            testContext.register(namingConvention);
            testContext.setNamingConvention(namingConvention);
        }
        if (S2SpockInternalTestContext.class.isInstance(testContext)) {
            S2SpockInternalTestContext.class.cast(testContext).setIterationInfo(invocation.getIteration());
        }

        for (FieldInfo fieldInfo : featureInfo.getParent().getAllFields()) {
            final Field field = fieldInfo.getReflection();
            final Class<?> fieldClass = field.getType();
            if (isAutoBindable(field)
                    && fieldClass.isAssignableFrom(testContext.getClass())
                    && fieldClass
                            .isAnnotationPresent(PublishedTestContext.class)) {
                field.setAccessible(true);
                if (ReflectionUtil.getValue(field, test) != null) {
                    continue;
                }
                bindField(field, testContext, test);
            }
        }
    }

    /**
     * オリジナルのクラスローダーを返します。
     *
     * @return オリジナルのクラスローダー
     */
    protected ClassLoader getOriginalClassLoader() {
        S2Container configurationContainer = S2ContainerFactory
                .getConfigurationContainer();
        if (configurationContainer != null
                && configurationContainer.hasComponentDef(ClassLoader.class)) {
            return ClassLoader.class.cast(configurationContainer
                    .getComponent(ClassLoader.class));
        }
        return Thread.currentThread().getContextClassLoader();
    }

    /**
     * ルートのコンテナを返します。
     *
     * @param testClass
     *            テストクラス
     * @param method
     *            テストメソッド
     * @return ルートのコンテナ
     */
    protected S2Container createRootContainer(Class<?> testClass, Method method) {
        final String rootDicon = introspector.getRootDicon(testClass, method);
        if (StringUtil.isEmpty(rootDicon)) {
            return S2ContainerFactory.create(s2junit4Path);
        }
        S2Container container = S2ContainerFactory.create(rootDicon);
        S2ContainerFactory.include(container, s2junit4Path);
        return container;
    }

    /**
     * テストコンテキストを解放します。
     *
     * @throws Throwable
     *            何らかの例外またはエラーが起きた場合
     */
    protected void tearDownTestContext() throws Throwable {
        testContext = null;
        DisposableUtil.dispose();
        S2ContainerBehavior
                .setProvider(new S2ContainerBehavior.DefaultProvider());
        Thread.currentThread().setContextClassLoader(originalClassLoader);
        unitClassLoader = null;
        originalClassLoader = null;
    }

    /**
     * コンテナを初期化します。
     */
    protected void initContainer() {
        testContext.include();
        testContext.initContainer();
    }

    /**
     * フィールドにコンポーネントをバインディングします。
     *
     * @param invocation
     *            メソッド呼び出し
     * @throws Throwable 何らかの例外またはエラーが発生した場合
     */
    protected void bindFields(IMethodInvocation invocation) throws Throwable {
        Object test = invocation.getTarget();
        for (FieldInfo fieldInfo : invocation.getSpec().getAllFields()) {
            bindField(fieldInfo.getReflection(), test);
        }
    }

    /**
     * 指定されたフィールドにコンポーネントをバインディングします。
     *
     * @param field
     *            フィールド
     * @param test
     *            テストオブジェクト
     */
    protected void bindField(final Field field, Object test) {
        if (isAutoBindable(field)) {
            field.setAccessible(true);
            if (ReflectionUtil.getValue(field, test) != null) {
                return;
            }
            final String name = resolveComponentName(field);
            Object component = null;
            if (testContext.hasComponentDef(name)) {
                component = testContext.getComponent(name);
                if (component != null) {
                    Class<?> componentClass = component.getClass();
                    if (!field.getType().isAssignableFrom(componentClass)) {
                        component = null;
                    }
                }
            }
            if (component == null
                    && testContext.hasComponentDef(field.getType())) {
                component = testContext.getComponent(field.getType());
            }
            if (component != null) {
                bindField(field, component, test);
            }
        }
    }

    /**
     * 指定されたフィールドに指定された値をバインディングします。
     *
     * @param field
     *            フィールド
     * @param object
     *            値
     * @param test
     *            テストオブジェクト
     */
    protected void bindField(final Field field, final Object object, final Object test) {
        ReflectionUtil.setValue(field, test, object);
        boundFields.add(field);
    }

    /**
     * 自動フィールドバインディングが可能な場合<code>true</code>を返します。
     *
     * @param field
     *            フィールド
     * @return 自動フィールドバインディングが可能な場合<code>true</code>、そうでない場合<code>false</code>
     */
    protected boolean isAutoBindable(final Field field) {
        final int modifiers = field.getModifiers();
        return !Modifier.isStatic(modifiers) && !Modifier.isFinal(modifiers)
                && !field.getType().isPrimitive();
    }

    /**
     * フィールドからコンポーネントの名前を解決します。
     *
     * @param filed
     *            フィールド
     * @return コンポーネント名
     */
    protected String resolveComponentName(final Field filed) {
        if (testContext.isEjb3Enabled()) {
            final EJB ejb = filed.getAnnotation(EJB.class);
            if (ejb != null) {
                if (!StringUtil.isEmpty(ejb.beanName())) {
                    return ejb.beanName();
                } else if (!StringUtil.isEmpty(ejb.name())) {
                    return ejb.name();
                }
            }
        }
        return normalizeName(filed.getName());
    }

    /**
     * コンポーネント名を正規化します。
     *
     * @param name
     *            コンポーネント名
     * @return 正規化されたコンポーネント名
     */
    protected String normalizeName(final String name) {
        return StringUtil.replace(name, "_", "");
    }

    /**
     * フィールドとコンポーネントのバインディングを解除します。
     *
     * @param invocation
     *            メソッド呼び出し
     * @throws Throwable
     */
    protected void unbindFields(IMethodInvocation invocation) throws Throwable {
        Object test = invocation.getTarget();
        for (final Field field : boundFields) {
            try {
                field.set(test, null);
            } catch (IllegalArgumentException e) {
                System.err.println(e);
            } catch (IllegalAccessException e) {
                System.err.println(e);
            }
        }
        boundFields = null;
    }

    /**
     * テストを実行します。
     * <p>
     * JTAが利用可能な場合、トランザクションの制御とテストデータの準備を行います。
     * </p>
     *
     * @param invocation
     *            メソッド呼び出し
     * @throws Throwable
     *             何らかの例外またはエラーが発生した場合
     */
    protected void runTest(IMethodInvocation invocation) throws Throwable {
        if (!testContext.isJtaEnabled()) {
            invocation.proceed();
            return;
        }
        Class<?> testClass = invocation.getTarget().getClass();
        Method method = invocation.getMethod().getReflection();
        TransactionManager tm = null;
        if (introspector.needsTransaction(testClass, method)) {
            try {
                tm = testContext.getComponent(TransactionManager.class);
                tm.begin();
            } catch (Throwable t) {
                System.err.println(t);
            }
        }
        boolean testFailed = false;
        try {
            testContext.prepareTestData();
            invocation.proceed();
        } catch (Throwable t) {
            testFailed = true;
            throw t;
        } finally {
            if (tm != null) {
                if (!testFailed && requiresTransactionCommitment(testClass, method)) {
                    tm.commit();
                } else {
                    tm.rollback();
                }
            }
        }
    }

    /**
     * トランザクションをコミットするように設定されている場合に<code>true</code>を返します。
     *
     * @param testClass
     *            テストクラス
     * @param method
     *            テストメソッド
     * @return トランザクションをコミットするように設定されている場合に<code>true</code>
     *         、そうでない場合<code>false</code>
     */
    protected boolean requiresTransactionCommitment(Class<?> testClass, Method method) {
        return introspector
                    .requiresTransactionCommitment(testClass, method);
    }

    /**
     * WARM deployが必要とされる場合<code>true</code>を返します。
     *
     * @param testClass
     *            テストクラス
     * @param method
     *            テストメソッド
     * @return WARM deployが必要とされる場合<code>true</code>、そうでない場合<code>false</code>
     */
    protected boolean needsWarmDeploy(Class<?> testClass, Method method) {
        return introspector.needsWarmDeploy(testClass, method)
                && !ResourceUtil.isExist("s2container.dicon")
                && ResourceUtil.isExist("convention.dicon")
                && ResourceUtil.isExist("creator.dicon")
                && ResourceUtil.isExist("customizer.dicon");
    }
}
