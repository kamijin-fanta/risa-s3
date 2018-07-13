import sbt.Keys._
import sbt.{Def, _}

object SlickGen extends AutoPlugin {

  //  override lazy val projectSettings = Seq((sourceGenerators in Compile) <<= slickGenCommand)

  object autoImport {
    val slickGen = taskKey[Seq[File]]("gen")
    val slickGenEvery = settingKey[Boolean]("every generate code")
  }

  import autoImport._

  override lazy val projectSettings: Seq[Def.Setting[_]] = Seq(
    slickGen := slickGenTask.value,
    slickGenEvery := false
  )

  lazy val slickGenTask = Def.task {
    val outputDir = (sourceManaged.value / "slick").getPath
    val r = (runner in Compile).value
    val s = streams.value
    val cp = (Compile / dependencyClasspath).value

    val filename = outputDir + "/Tables.scala"
    val generatedFile = Seq(file(filename))

    if (!slickGenEvery.value && generatedFile.forall(_.canRead)) {
      generatedFile
    } else {
      // compile if not exist file
      val url = "jdbc:mysql://172.24.2.1:4000/risa"
      val jdbcDriver = "com.mysql.jdbc.Driver"
      val slickDriver = "slick.jdbc.MySQLProfile"
      val pkg = ""
      val user = "root"
      val pass = ""

      println("Run task...")
      r.run(
        "slick.codegen.SourceCodeGenerator",
        cp.files,
        Array(slickDriver, jdbcDriver, url, outputDir, pkg, user, pass),
        s.log
      )

      slickGenEvery := true
      generatedFile
    }
  }
}
