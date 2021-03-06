package io.hydrosphere.mist.master.data

import java.nio.file.Paths

import io.hydrosphere.mist.master
import io.hydrosphere.mist.master.TestUtils
import io.hydrosphere.mist.master.models.ContextConfig
import org.apache.commons.io.FileUtils
import org.scalatest.{BeforeAndAfter, FunSpec, Matchers}

import scala.concurrent.duration._

class ContextsStorageSpec extends FunSpec with Matchers with BeforeAndAfter {

  val path = "./target/data/ctx_store_test"

  before {
    val f = Paths.get(path).toFile
    if (f.exists()) FileUtils.deleteDirectory(f)
  }

  import scala.concurrent.ExecutionContext.Implicits.global
  import master.TestUtils._

  it("should update") {
    val contexts = testStorage()

    val ctx = ContextConfig("new", Map.empty, Duration.Inf, 50, false, "weq", 10 second)
    contexts.update(ctx).await
    contexts.get("new").await.isDefined shouldBe true
  }

  it("should return defalts") {
    val contexts = testStorage()

    val ctx = ContextConfig("new", Map.empty, Duration.Inf, 50, false, "weq", 10 second)
    contexts.update(ctx).await

    contexts.all.await.map(_.name) should contain allOf ("default", "foo", "new")
  }

  it("should fallback to default") {
    val contexts = testStorage()
    val expected = TestUtils.contextSettings.default.copy(name = "new")
    contexts.getOrDefault("new").await shouldBe expected
  }

  it("should return precreated") {
    val contexts = testStorage()
    val ctx = ContextConfig("new", Map.empty, Duration.Inf, 50, true, "weq", 10 second)

    contexts.update(ctx).await
    contexts.precreated.await should contain only(ctx)
  }

  it("should return default") {
    val contexts = testStorage()
    contexts.get("default").await.isDefined shouldBe true
  }

  it("should override settings") {
    val contexts = testStorage()

    val ctx = ContextConfig("foo", Map.empty, Duration.Inf, 50, true, "FOOOOPT", 10 second)
    contexts.get("foo").await.get.runOptions shouldNot be (ctx.runOptions)

    contexts.update(ctx).await

    contexts.get("foo").await.get shouldBe ctx
  }

  def testStorage(): ContextsStorage = {
    new ContextsStorage(
      FsStorage.create(path, ConfigRepr.ContextConfigRepr),
      TestUtils.contextSettings
    )
  }
}
