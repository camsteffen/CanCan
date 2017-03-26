name := "CanCan"

organization := "com.github.wpm.cancan"

version := "1.0.0"

scalaVersion := "2.10.6"

scalacOptions ++= Seq("-unchecked", "-deprecation")

initialCommands in console := "import com.github.wpm.cancan._"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1" % "test"
