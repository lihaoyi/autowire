val str1 =
  """
    |[info]
    |[info]                        * - compileError("123.call()").check(
    |[info]                                          ^
  """.stripMargin

val str2 =
  """
    |[info]
    |[info]                        * - compileError("123.call()").check(
    |[info]                                          ^
  """.stripMargin

str1 == str2

val stripped = str1.reverse.dropWhile("\n ".toSet.contains).reverse
val normalizedPos = "\n" + str2

str1 == str2