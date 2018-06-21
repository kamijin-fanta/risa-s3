object JvmOptions {
  val options = {
    val curr = sys.props("java.specification.version")
    
    curr match {
      case "1.9" =>
        // java9 support https://github.com/aws/aws-sdk-java/issues/1092
        "--add-modules=java.xml.bind,java.activation"
      case _ => ""
    }
  }
}
