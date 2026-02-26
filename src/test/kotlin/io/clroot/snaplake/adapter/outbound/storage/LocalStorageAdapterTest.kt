package io.clroot.snaplake.adapter.outbound.storage

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import java.nio.file.Files

class LocalStorageAdapterTest :
    DescribeSpec({

        val tempDir = Files.createTempDirectory("snaplake-test")
        val adapter = LocalStorageAdapter(tempDir.toString())

        afterSpec {
            tempDir.toFile().deleteRecursively()
        }

        describe("write and read") {
            it("파일을 쓰고 읽는다") {
                val data = "hello".toByteArray()
                adapter.write("test/file.parquet", data)

                val result = adapter.read("test/file.parquet")
                result shouldBe data
            }
        }

        describe("list") {
            it("prefix로 파일 목록을 조회한다") {
                adapter.write("ds1/daily/2026-02-25/public.users.parquet", byteArrayOf(1))
                adapter.write("ds1/daily/2026-02-25/public.orders.parquet", byteArrayOf(2))
                adapter.write("ds2/daily/2026-02-25/public.users.parquet", byteArrayOf(3))

                val files = adapter.list("ds1/daily/2026-02-25/")
                files shouldHaveSize 2
            }
        }

        describe("delete") {
            it("파일을 삭제한다") {
                adapter.write("to-delete.parquet", byteArrayOf(1))
                adapter.delete("to-delete.parquet")

                adapter.exists("to-delete.parquet") shouldBe false
            }
        }

        describe("deleteAll") {
            it("prefix 아래 모든 파일과 디렉토리를 삭제한다") {
                adapter.write("deleteall/a.parquet", byteArrayOf(1))
                adapter.write("deleteall/sub/b.parquet", byteArrayOf(2))

                adapter.deleteAll("deleteall")

                adapter.exists("deleteall/a.parquet") shouldBe false
                adapter.exists("deleteall/sub/b.parquet") shouldBe false
            }
        }

        describe("exists") {
            it("존재하는 파일은 true를 반환한다") {
                adapter.write("exists-test.parquet", byteArrayOf(1))
                adapter.exists("exists-test.parquet") shouldBe true
            }

            it("존재하지 않는 파일은 false를 반환한다") {
                adapter.exists("nonexistent.parquet") shouldBe false
            }
        }

        describe("getUri") {
            it("절대 파일 경로를 반환한다") {
                adapter.write("uri-test.parquet", byteArrayOf(1))
                val uri = adapter.getUri("uri-test.parquet")
                uri shouldStartWith "/"
            }

            it("공백이 포함된 경로도 인코딩 없이 반환한다") {
                adapter.write("Sample DB/test.parquet", byteArrayOf(1))
                val uri = adapter.getUri("Sample DB/test.parquet")
                uri shouldStartWith "/"
                uri shouldContain "Sample DB"
            }
        }

        describe("testConnection") {
            it("쓰기 가능한 디렉토리면 true를 반환한다") {
                adapter.testConnection() shouldBe true
            }
        }

        describe("path traversal 방어") {
            val traversalPath = "../etc/passwd"

            it("write에서 path traversal을 차단한다") {
                shouldThrow<IllegalArgumentException> {
                    adapter.write(traversalPath, byteArrayOf(1))
                }.message shouldContain "Path traversal detected"
            }

            it("read에서 path traversal을 차단한다") {
                shouldThrow<IllegalArgumentException> {
                    adapter.read(traversalPath)
                }.message shouldContain "Path traversal detected"
            }

            it("delete에서 path traversal을 차단한다") {
                shouldThrow<IllegalArgumentException> {
                    adapter.delete(traversalPath)
                }.message shouldContain "Path traversal detected"
            }

            it("deleteAll에서 path traversal을 차단한다") {
                shouldThrow<IllegalArgumentException> {
                    adapter.deleteAll(traversalPath)
                }.message shouldContain "Path traversal detected"
            }

            it("list에서 path traversal을 차단한다") {
                shouldThrow<IllegalArgumentException> {
                    adapter.list(traversalPath)
                }.message shouldContain "Path traversal detected"
            }

            it("exists에서 path traversal을 차단한다") {
                shouldThrow<IllegalArgumentException> {
                    adapter.exists(traversalPath)
                }.message shouldContain "Path traversal detected"
            }

            it("getUri에서 path traversal을 차단한다") {
                shouldThrow<IllegalArgumentException> {
                    adapter.getUri(traversalPath)
                }.message shouldContain "Path traversal detected"
            }

            it("정상 경로는 허용한다") {
                adapter.write("safe/path/file.parquet", byteArrayOf(1))
                adapter.exists("safe/path/file.parquet") shouldBe true
            }

            it("../가 포함되어도 normalize 후 root 내부면 허용한다") {
                adapter.write("a/b/../c/file.parquet", byteArrayOf(1))
                adapter.exists("a/c/file.parquet") shouldBe true
            }
        }
    })
