# Java Code Generation Specification:

## Preamble

1. The goal of the current spec is to change **only** Pkl classes production, leaving the rest of the generation intact
2. Each custom immutable Java class with the one-property withers to be replaced by:
  1. each Pkl abstract class would be generated as the corresponding Java interface, such that:
    1. each its declared public property would become the `interface`'s abstract method of the same `type` and `name
    2. the `interface` would implement the Pkl abstract class's superclass, if present
  2. each Pkl class, including modules, would be generated as the corresponding Java record, such that:
    1. `record`'s components are identical to the current custom Java class
    2. `record` would implement its Pkl superclass corresponding Java interface
    3. `record` would in addition implement the common generic `Wither` interface (in line with https://openjdk.org/jeps/468 which is not available yet)
    4. `record` would have its special `Memento` public static inner class generated as described below
  3. each Pkl `open` class would in addition have its default interface generated like in the case of a Pkl abstract class
3. The following would be generated as the singleton common constructs for all:
```java

import java.util.function.Consumer;

public interface Wither<R extends Record, S> {
   R with(Consumer<S> setter);
}

```
4. The record `R`, its `Memento` would be generated as follows:
```java

record R(String p1, String p2, String p3) implements Wither<R, R.Memento>, Serializable {

   @Override
   public R with(final Consumer<Memento> setter) {
      final var memento = new Memento(this);
      setter.accept(memento);
      return memento.build();
   }

   public static final class Memento {
      public String p1;
      public String p2;
      public String p3;

      private Memento(final R r) {
         p1 = r.p1;
         p2 = r.p2;
         p3 = r.p3;
      }

      private R build() {
         return new R(p1, p2, p3);
      }
   }
}

```
5. The usage in the Java consumer code would be as follows:
```java

class Scratch {

  public static void main(String[] args) {
     final R r1 = new R("a", "b", "c");
     final R r2 = r1.with(it -> it.p1 = "a2").with(it -> {
        it.p2 = "b2";
        it.p3 = "c2";
     });

    System.out.println(r1); // R[p1=a, p2=b, p3=c]
    System.out.println(r2); // R[p1=a2, p2=b2, p3=c2]
  }
}


```
6. Given Pkl is single inheritance and doesn't support creation or extension of generic classes, the above generation scheme should be sufficient and adequate.
7. As an extension API, the generation offers the option to expose empty base interface(-s) to be extended by the Java consumer as follows:
  1. one base interface implemented by all generated records as follows:
    1. `IPklBase` interface code would have to be implemented elsewhere in the Java consumer code, otherwise causing the compilation error
       ```java
       record R(/* component list of <Type name> */) implements Wither<R, R.Memento>, IPklBase {
         // ...
       }
       ```
    2. Most likely, `IPklBase` would have the default methods, thus effectively extending the functionality of all classes
  2. a base interface per record type, to be implemented elsewhere in the Java consumer code similar to above:
     ```java
        record R(/* component list of <Type name> */) implements Wither<R, R.Memento>, IR {
          // ...
        }
     ```
8. Serialization would be delegated to the Record API as follows:
  1. if requested in options, each generated Java record would in addition implement a Java `java.io.Serializable`

> [!IMPORTANT]
> All the annotation, name handling regarding Java reserved words, and such would be handled as currently.

<details>

<summary>See a complete example of Java code generation</summary>

```java

package com.apple.pkl.code.gen.java.example;

import java.io.Serializable;
import java.util.function.Consumer;

class Demo implements Serializable {

   public static void main(final String[] args) {
      final R r1 = new R("a", "b", "c");
      final R r2 = r1.with(it -> it.p1 = "a2").with(it -> {
         it.p2 = "b2";
         it.p3 = "c2";
      });

      System.out.println(r1);
      System.out.println(r2);
   }
}

//TODO: include as-is once
interface Wither<R extends Record, S> {
   R with(Consumer<S> setter);
}

//TODO: include per Pkl class
record R(String p1, String p2, String p3) implements Wither<R, R.Memento>, Serializable {

   @Override
   public R with(final Consumer<Memento> setter) {
      final var memento = new Memento(this);
      setter.accept(memento);
      return memento.build();
   }

   public static final class Memento {

      public String p1;
      public String p2;
      public String p3;

      private Memento(final R r) {
         p1 = r.p1;
         p2 = r.p2;
         p3 = r.p3;
      }

      private R build() {
         return new R(p1, p2, p3);
      }
   }
}

```

</details>
