name := """aurora-sample"""

version := "1.0"

scalaVersion := "2.11.1"

resolvers ++= Seq(
    "Sonatype OSS Release Repository" at "https://oss.sonatype.org/content/repositories/releases/",
    "Sonatype OSS Snapshots Repository" at "https://oss.sonatype.org/content/repositories/snapshots"
)

libraryDependencies += "net.gree.aurora" %% "aurora-scala" % "0.0.3" excludeAll(
    ExclusionRule("org.clapper", "grizzled-slf4j_2.10"),
    ExclusionRule("org.sisioh", "scala-dddbase-core_2.10")
)
