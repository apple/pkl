val assembleNative by tasks.registering {}

val testNative by tasks.registering {}

val checkNative by tasks.registering {
  dependsOn(testNative)
}

val buildNative by tasks.registering {
  dependsOn(assembleNative, checkNative)
}
