organization := "Scalatron"

name         := "Scalatron"

version in Global := "1.1.0.2"

scalaVersion in ThisBuild := "2.11.7"


libraryDependencies ++= Seq(
)

fork in ( Test, run ) := true
//scalacOptions in ThisBuild ++= Seq ("-feature", "-deprecation")
//autoScalaLibrary := false
