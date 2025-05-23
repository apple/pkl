package org.pkl.config.scala.annotation;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EnumOwner {

  Class<? extends scala.Enumeration> value();

}
