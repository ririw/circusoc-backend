instrumentSettings

ScoverageKeys.highlighting := true

name := """com.circusoc.backend"""

version := "1.0"

scalaVersion := "2.10.2"

resolvers ++= Seq(
  "spray repo" at "http://repo.spray.io",
  "spray nightlies" at "http://nightlies.spray.io"
)

libraryDependencies ++= Seq(
  "com.typesafe.akka"  %% "akka-actor"       % "2.2.0",
  "com.typesafe.akka"  %% "akka-slf4j"       % "2.2.0",
  "ch.qos.logback"      % "logback-classic"  % "1.0.13",
  "io.spray"            % "spray-can"        % "1.2-20130712",
  "io.spray"            % "spray-routing"    % "1.2-20130712",
  "io.spray"           %% "spray-json"       % "1.2.6",
  "org.scalatest"      %% "scalatest"        % "2.1.6"            % "test",
  "org.scalacheck"     %% "scalacheck"       % "1.11.5"           % "test",
  "io.spray"            % "spray-testkit"    % "1.2-20130712"     % "test",
  "com.typesafe.akka"  %% "akka-testkit"     % "2.2.0"            % "test",
  "org.scalikejdbc"    %% "scalikejdbc"      % "2.1.1",
  "com.h2database"      % "h2"               % "1.4.181",
  "org.mindrot"         % "jbcrypt"          % "0.3m",
  "org.joda"            % "joda-convert"     % "1.2",
  "org.dbunit"          % "dbunit"           % "2.5.0"            % "test",
  "org.xerial"          % "sqlite-jdbc"      % "3.8.5-pre1"       % "test",
  "org.hsqldb"          % "hsqldb"           % "2.3.2",
  "org.codemonkey.simplejavamail" % "simple-java-mail"            % "2.1",
  "org.scalamock"                %% "scalamock-scalatest-support" % "3.0.1" % "test"
)

scalacOptions ++= Seq(
  "-unchecked",
  "-deprecation",
  "-Xlint",
  "-Ywarn-dead-code",
  "-language:_",
  "-target:jvm-1.7",
  "-encoding", "UTF-8"
)

testOptions in Test += Tests.Argument("-oF")