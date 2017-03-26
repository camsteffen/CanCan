name := "CanCan"

organization := "com.github.wpm.cancan"

version := "1.0.0"

scalaVersion := "2.12.1"

scalacOptions ++= Seq("-unchecked", "-deprecation")

initialCommands in console := "import com.github.wpm.cancan._"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1" % "test"

libraryDependencies += "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.5"