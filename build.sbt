ThisBuild / organization := "toy.io"

lazy val root = project
  .in(file("."))
  .settings(
    name := "tio",
    description := "Toy IO Monad",
    version := "1.8.0",
    scalaVersion := "2.13.10"
  )
