import sbt.Keys._
import sbt.{Def, _}

object SlickGen extends AutoPlugin {

  //  override lazy val projectSettings = Seq((sourceGenerators in Compile) <<= slickGenCommand)

  object autoImport {
    val slickGen = taskKey[Unit]("gen")
  }

  import autoImport._

  override lazy val projectSettings: Seq[Def.Setting[_]] = Seq(
    slickGen := slickGenTask.value
  )

  lazy val slickGenTask = Def.task {
    val r = (runner in Compile).value
    val s = streams.value

    val outputDir = (sourceManaged.value / "slick").getPath
    val url = "jdbc:mysql://172.24.2.1:4000/risa"
    val jdbcDriver = "com.mysql.jdbc.Driver"
    val slickDriver = "slick.jdbc.MySQLProfile"
    val pkg = "demo"
    val user = "root"
    val pass = ""

    println("Run task...")
    r.run(
      "slick.codegen.SourceCodeGenerator",
      (Compile / dependencyClasspath).value.files,
      Array(slickDriver, jdbcDriver, url, outputDir, pkg, user, pass),
      s.log
    )
    ()
  }
}
