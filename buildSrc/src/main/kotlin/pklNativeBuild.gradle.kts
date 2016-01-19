val assembleNative by tasks.registering {}

val checkNative by tasks.registering {}

val buildNative by tasks.registering {
  dependsOn(assembleNative, checkNative)
}
