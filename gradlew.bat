@ECHO OFF
SETLOCAL
SET APP_HOME=%~dp0
SET CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
IF NOT EXIST "%CLASSPATH%" (
  ECHO gradle-wrapper.jar missing
  EXIT /B 1
)
java.exe -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
