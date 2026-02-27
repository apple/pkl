# Scala bindings for PKL language

## Covered
- classes and case classes
- Scala `Option` for nullable PKL types
- Scala `Regexp` for PKL string/regexp
- Scala `Tuple2` for PKL Pair
- Scala `Duration` and `FiniteDuration` for PKL Duration
- Java `Instant` for PKL int and String (how about the rest of java.time?)
- Collections
  - `immutable.Seq` 
  - `immutable.Vector` 
  - `immutable.List` 
  - `immutable.Set` 
  - `immutable.Map` 
  - `immutable.Stream` 
  - `immutable.LazyList` 
  - `mutable.Map` 
  - `mutable.Set` 
  - `mutable.Seq` 
  - `mutable.Buffer` 
  - `mutable.Queue` 
  - `mutable.Stack` 
- Scala Enumeration (if annotated)
  Scala 2 Enumeration is a runtime construct, we can't access it's members from Type referense.
  To work around it, we introduced `@EnumOwner` annotation which you can use like below:  
  ```
  object SimpleEnum extends Enumeration {
  
     @EnumOwner(classOf[SimpleEnum.type])
     case class V() extends Val(nextId)
  
     val Aaa = V()
     val Bbb = V()
     val Ccc = V()
  } 
  ```
  
## TODO
- more tests
- `Either` (???)
- `sealed traits`
- `object` instances
- cross-version compilation to cover scala `2.12` too
