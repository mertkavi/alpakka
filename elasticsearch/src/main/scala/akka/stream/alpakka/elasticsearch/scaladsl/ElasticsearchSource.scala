/*
 * Copyright (C) 2016-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.stream.alpakka.elasticsearch.scaladsl

import akka.NotUsed
import akka.stream.alpakka.elasticsearch._
import akka.stream.scaladsl.Source
import org.elasticsearch.client.RestClient
import spray.json._

/**
 * Scala API to create Elasticsearch sources.
 */
object ElasticsearchSource {

  /**
   * Creates a [[akka.stream.scaladsl.Source]] from Elasticsearch that streams [[OutgoingMessage]]s
   * of Spray's [[spray.json.JsObject]].
   * Alias of [[create]].
   */
  def apply(indexName: String,
            typeName: String,
            query: String,
            settings: ElasticsearchSourceSettings = ElasticsearchSourceSettings())(
      implicit client: RestClient
  ): Source[OutgoingMessage[JsObject], NotUsed] = create(indexName, typeName, query, settings)

  /**
   * Creates a [[akka.stream.scaladsl.Source]] from Elasticsearch that streams [[OutgoingMessage]]s
   * of Spray's [[spray.json.JsObject]].
   */
  def create(indexName: String,
             typeName: String,
             query: String,
             settings: ElasticsearchSourceSettings = ElasticsearchSourceSettings())(
      implicit client: RestClient
  ): Source[OutgoingMessage[JsObject], NotUsed] =
    Source.fromGraph(
      new ElasticsearchSourceStage(
        indexName,
        typeName,
        query,
        client,
        settings,
        new SprayJsonReader[JsObject]()(DefaultJsonProtocol.RootJsObjectFormat)
      )
    )

  /**
   * Creates a [[akka.stream.scaladsl.Source]] from Elasticsearch that streams [[OutgoingMessage]]s of type `T`
   * converted by Spray's [[spray.json.JsonReader]]
   */
  def typed[T](indexName: String,
               typeName: String,
               query: String,
               settings: ElasticsearchSourceSettings = ElasticsearchSourceSettings())(
      implicit client: RestClient,
      reader: JsonReader[T]
  ): Source[OutgoingMessage[T], NotUsed] =
    Source.fromGraph(
      new ElasticsearchSourceStage(indexName, typeName, query, client, settings, new SprayJsonReader[T]()(reader))
    )

  private class SprayJsonReader[T](implicit reader: JsonReader[T]) extends MessageReader[T] {

    override def convert(json: String): ScrollResponse[T] = {
      val jsObj = json.parseJson.asJsObject
      jsObj.fields.get("error") match {
        case Some(error) => {
          ScrollResponse(Some(error.toString), None)
        }
        case None => {
          val scrollId = jsObj.fields("_scroll_id").asInstanceOf[JsString].value
          val hits = jsObj.fields("hits").asJsObject.fields("hits").asInstanceOf[JsArray]
          val messages = hits.elements.reverse.map { element =>
            val doc = element.asJsObject
            val id = doc.fields("_id").asInstanceOf[JsString].value
            val source = doc.fields("_source").asJsObject
            OutgoingMessage(id, source.convertTo[T])
          }
          ScrollResponse(None, Some(ScrollResult(scrollId, messages)))
        }
      }
    }

  }

}
