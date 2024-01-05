package Shades

import Shades.ParsedData.fileParser

class CodecSuite extends munit.FunSuite:
	test("sanity check") {
		val result = fileParser.parseAll(Data.content)
		assert(result.isRight, result)
	}
