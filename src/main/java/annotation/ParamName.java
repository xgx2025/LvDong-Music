package annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.PARAMETER) // 用于参数
@Retention(RetentionPolicy.RUNTIME) // 运行时可见
public @interface ParamName {
    String value(); // 参数名称
}
