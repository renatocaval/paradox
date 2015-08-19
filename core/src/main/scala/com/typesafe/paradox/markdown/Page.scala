/*
 * Copyright © 2015 Typesafe, Inc. <http://www.typesafe.com>
 */

package com.typesafe.paradox.markdown

import com.typesafe.paradox.tree.Tree.{ Forest, Location }
import java.io.File
import java.net.URI
import org.pegdown.ast.{ Node, RootNode, SpecialTextNode, TextNode }
import scala.annotation.tailrec

/**
 * Common interface for Page and Header, which are linkable.
 */
sealed abstract class Linkable {
  def path: String
  def label: Node
}

/**
 * Header in a page, with anchor path and markdown nodes.
 */
case class Header(path: String, label: Node) extends Linkable

/**
 * Markdown page with target path, parsed markdown, and headers.
 */
case class Page(file: File, path: String, label: Node, headers: Forest[Header], markdown: RootNode) extends Linkable {
  /**
   * Path to the root of the site.
   */
  val base: String = Path.basePath(path)

  /**
   * Extract a page title from text nodes in the label.
   */
  val title: String = {
    import scala.collection.JavaConverters._
    def textNodes(node: Node): Seq[String] = {
      node.getChildren.asScala.flatMap {
        case t: TextNode => Seq(t.getText)
        case other       => textNodes(other)
      }
    }
    textNodes(label).mkString
  }
}

object Page {
  /**
   * Create a single page from parsed markdown.
   */
  def apply(path: String, markdown: RootNode): Page = {
    apply(path, markdown, identity)
  }

  /**
   * Create a single page from parsed markdown.
   */
  def apply(path: String, markdown: RootNode, convertPath: String => String): Page = {
    convertPage(convertPath)(Index.page(new File(path), path, markdown))
  }

  /**
   * Convert parsed markdown pages into a linked forest of Page objects.
   */
  def forest(parsed: Seq[(File, String, RootNode)], convertPath: String => String): Forest[Page] = {
    Index.pages(parsed) map (_ map convertPage(convertPath))
  }

  /**
   * Convert an Index.Page into the final Page and Headers.
   * The first h1 header is used for the page header and title.
   */
  def convertPage(convertPath: String => String)(page: Index.Page): Page = {
    // TODO: get default label node from page index link?
    val targetPath = convertPath(page.path)
    val (label: Node, subheaders) = page.headers match {
      case h :: hs if h.label.level == 1 => (h.label.markdown, h.children ++ hs)
      case hs                            => (new SpecialTextNode(targetPath), hs)
    }
    val headers = subheaders map (_ map (h => Header(h.path, h.markdown)))
    Page(page.file, targetPath, label, headers, page.markdown)
  }

  /**
   * Collect all page paths.
   */
  def allPaths(pages: Forest[Page]): List[String] = {
    @tailrec
    def collect(location: Option[Location[Page]], paths: List[String] = Nil): List[String] = location match {
      case Some(loc) => collect(loc.next, loc.tree.label.path :: paths)
      case None      => paths
    }
    pages flatMap { root => collect(Some(root.location)) }
  }

}

/**
 * Helper methods for paths.
 */
object Path {
  /**
   * Form a relative path to the root, based on the number of directories in a path.
   */
  def basePath(path: String): String = {
    "../" * path.count(_ == '/')
  }

  /**
   * Resolve a relative path against a base path.
   */
  def resolve(base: String, path: String): String = {
    new URI(base).resolve(path).getPath
  }

  /**
   * Replace the file extension in a path.
   */
  def replaceExtension(from: String, to: String)(link: String): String = {
    val uri = new URI(link)
    replaceSuffix(from, to)(uri.getPath) + Option(uri.getFragment).fold("")("#".+)
  }

  /**
   * Replace the suffix of a path.
   */
  def replaceSuffix(from: String, to: String)(path: String): String = {
    if (path.endsWith(from)) path.dropRight(from.length) + to else path
  }
}
