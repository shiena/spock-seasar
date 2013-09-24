# Spock Framework - Seasar2 Module

spock-seasar is integration with [Spock Framework](http://spockframework.org/)
and [S2JUnit4](http://s2container.seasar.org/2.4/ja/S2JUnit4.html)

## Example

Rewrite to Spock from [a sample of S2JUnit4](http://s2container.seasar.org/2.4/ja/S2JUnit4.html#sample)

```groovy
package examples.s2junit4

import static org.seasar.framework.unit.S2Assert.assertEquals

import org.shiena.seasar.Seasar2Support
import org.junit.Ignore
import org.seasar.framework.unit.TestContext
import spock.lang.Specification

@Seasar2Support
class EmployeeDaoImplSpec extends Specification {

    TestContext ctx

    EmployeeDao dao

    def getEmployee() {
        when:
        def emp = dao.getEmployee(9900)

        then:
        assertEquals "1", ctx.expected, emp
    }

    @Ignore("not implemented.")
    def getEmployeeByName() {
    }

}
```

## How to use

To use with gradle, you may add the following snippet to your `build.gradle`

```groovy
repositories {
    maven {
        url 'https://bitbucket.org/shiena/mvn-repo/raw/tip/'
    }
}

dependencies {
    testCompile 'org.shiena:spock-seasar:0.0.1'
}
```

To use with maven, you may add the following snippet to your `pom.xml`

```xml
<repositories>
    <repository>
        <url>https://bitbucket.org/shiena/mvn-repo/raw/tip/</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>org.shiena</groupId>
        <artifactId>spock-seasar</artifactId>
        <version>0.0.1</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

## Additional features

### `org.shiena.seasar.S2SpockInternalTestContextImpl`

This is a `TestContext` that inherits `InternalTestContextImpl`.

* `getFeatureName()` - Return the String name of the test method
* `getIterationName()` - Return the String name of the unrolled test method

### `org.shiena.seasar.S2SpockSimpleInternalTestContext`

This is a `TestContext` that inherits `SimpleInternalTestContext`.

* `getFeatureName()` - Return the String name of the test method
* `getIterationName()` - Return the String name of the unrolled test method

## Unsupported features

* [Naming conventions for method](http://s2container.seasar.org/2.4/ja/S2JUnit4.html#methodNamingConvention)
* [`@Prerequisite` annotation](http://s2container.seasar.org/2.4/ja/S2JUnit4.html#prerequisiteAnnotation)
* [`@Mock` annotation](http://s2container.seasar.org/2.4/ja/S2JUnit4.html#mockAnnotation)
* [`@Mocks` annotation](http://s2container.seasar.org/2.4/ja/S2JUnit4.html#mocksAnnotation)
* [`@EasyMock` annotation](http://s2container.seasar.org/2.4/ja/S2JUnit4.html#easyMockAnnotation)
* [`@PostBindFields` annotation](http://s2container.seasar.org/2.4/ja/S2JUnit4.html#postBindFieldsAnnotation)
* [`@PreUnbindFields` annotation](http://s2container.seasar.org/2.4/ja/S2JUnit4.html#preUnbindFieldsAnnotation)
* [Customizing the behavior using `s2junit4config.dicon`](http://s2container.seasar.org/2.4/ja/S2JUnit4.html#customization)

## Attention

Can not be used together Spock and S2JUnit4 because it requires JUnit of different versions.

* Spock requires JUnit 4.7 or higher.
* S2JUnit4 requires JUnit 4.4.

## License

[The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)

