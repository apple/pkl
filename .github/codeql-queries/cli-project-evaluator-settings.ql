import java

from MethodCall mc, Field f, Method m
where
  // find all calls to Project.getEvaluatorSettings in CliCommand
  mc.getCompilationUnit().getFile().getBaseName() = "CliCommand.kt" and
  mc.getMethod().getName() = "getEvaluatorSettings" and
  mc.getMethod().getDeclaringType().getName() = "Project" and

  // find CliCommand.evaluatorSettings field delegate
  f.getFile().getBaseName() = "CliCommand.kt" and
  f.getName() = "evaluatorSettings$delegate" and

  // exclude the allowed call in CliCommand.evaluatorSettings
  m = f.getInitializer().(MethodCall).getArgument(0).(LambdaExpr).getAnonymousClass().getAMember() and
  m != mc.getCaller()
select mc, "CliCommand must access project evaluator settings via this.evaluatorSettings, not via this.project"
