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

import org.spockframework.runtime.extension.AbstractAnnotationDrivenExtension;
import org.spockframework.runtime.model.FeatureInfo;
import org.spockframework.runtime.model.SpecInfo;

/**
 * @author Mitsuhiro Koga
 */
public class S2Extension extends AbstractAnnotationDrivenExtension<Seasar2Support> {

    @Override
    public void visitSpecAnnotation(Seasar2Support seasar2Support, SpecInfo spec) {
        S2Interceptor interceptor = new S2Interceptor();

        spec.getSetupMethod().addInterceptor(interceptor);
        spec.getCleanupMethod().addInterceptor(interceptor);
        for (FeatureInfo feature : spec.getFeatures()) {
            feature.getFeatureMethod().addInterceptor(interceptor);
        }
    }

}
