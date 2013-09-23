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

import org.seasar.framework.unit.impl.InternalTestContextImpl;
import org.spockframework.runtime.model.IterationInfo;

/**
 * @author Mitsuhiro Koga
 */
public class S2SpockInternalTestContextImpl extends InternalTestContextImpl implements S2SpockInternalTestContext {

    /** 繰り返しのテストメソッドの情報 */
    protected IterationInfo iterationInfo;

    /**
     * {@inheritDoc}
     */
    public void setIterationInfo(IterationInfo iterationInfo) {
        this.iterationInfo = iterationInfo;
    }

    /**
     * {@inheritDoc}
     */
    public String getIterationName() {
        return iterationInfo.getName();
    }

    /**
     * {@inheritDoc}
     */
    public String getFeatureName() {
        return iterationInfo.getParent().getName();
    }
}
