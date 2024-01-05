package Shades

import cats.parse.*
import cats.data.NonEmptyList
import cats.Show

object ParsedData:

    val tab = Parser.char('\t')
    val dot = Parser.char('.').map(_ => CollationType.regular)
    val star = Parser.char('*').map(_ => CollationType.variable)

    extension (p: Parser[NonEmptyList[Char]])
        def toInt: Parser[Int] =
            p.map(chars => Integer.parseInt(chars.toList.mkString, 16))

    val fiveHexParser =
        Rfc5234.hexdig.rep(5, 5).toInt

    val fourHexParser =
        Rfc5234.hexdig.rep(4, 4).toInt

    val stringParser =
        (
            Parser.char(' ') | Rfc5234.alpha
        ).rep.map(chars => chars.toList.mkString)

    val typeParser =
        dot.orElse(star)

    val elementParser =
        (fourHexParser
            ~ (tab *> fourHexParser)
            ~ (tab *> fourHexParser)).map { case ((a, b), c) =>
            CollationElement(a, b, c)
        }

    val rowParser =
        fiveHexParser
            ~ (tab *> typeParser)
            ~ (tab *> elementParser)
            ~ (tab *> stringParser)
            <* Parser.char('\n')

    val fileParser =
        rowParser
            .map { case (((id, kind), element), name) =>
                CollationRule(id, kind, element, name)
            }
            .rep
            .map { rules =>
                CollationTable(rules.toList)
            }

    def errorToString(error: Parser.Error): String =
        Show[Parser.Error].show(error)
