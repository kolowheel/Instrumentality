name := """instrumentality"""

version := "1.0-SNAPSHOT"



lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.6"


resolvers := ("Atlassian Releases" at "https://maven.atlassian.com/public/") +: resolvers.value

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"

resolvers += Resolver.sonatypeRepo("snapshots")

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws,
  specs2 % Test,
  "org.reactivemongo" %% "play2-reactivemongo" % "0.11.7.play24",
  "org.scalaz" %% "scalaz-core" % "7.1.4",
  "org.typelevel" %% "scalaz-contrib-210" % "0.2",
  "io.megl" %% "play-json-extra" % "2.4.3",
  "com.mohiva" %% "play-silhouette" % "3.0.0",
  "net.codingwell" %% "scala-guice" % "4.0.0",
  "net.ceedubs" %% "ficus" % "1.1.2",
  "org.jsoup" % "jsoup" % "1.8.3"

)


resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"

// Play provides two styles of routers, one expects its actions to be injected, the
// other, legacy style, accesses its actions statically.
routesGenerator := InjectedRoutesGenerator


fork in run := false