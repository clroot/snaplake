package com.snaplake.adapter.outbound.storage

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import software.amazon.awssdk.core.ResponseBytes
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.*

class S3StorageAdapterTest : DescribeSpec({

    val s3Client = mockk<S3Client>()
    val bucket = "test-bucket"
    val adapter = S3StorageAdapter(s3Client, bucket)

    beforeTest {
        clearAllMocks()
    }

    describe("write") {
        it("S3에 파일을 업로드한다") {
            every { s3Client.putObject(any<PutObjectRequest>(), any<RequestBody>()) } returns
                PutObjectResponse.builder().build()

            adapter.write("test/file.parquet", byteArrayOf(1, 2, 3))

            verify {
                s3Client.putObject(
                    match<PutObjectRequest> {
                        it.bucket() == bucket && it.key() == "test/file.parquet"
                    },
                    any<RequestBody>(),
                )
            }
        }
    }

    describe("read") {
        it("S3에서 파일을 읽는다") {
            val data = byteArrayOf(1, 2, 3)
            every { s3Client.getObjectAsBytes(any<GetObjectRequest>()) } returns
                ResponseBytes.fromByteArray(
                    GetObjectResponse.builder().build(),
                    data,
                )

            val result = adapter.read("test/file.parquet")
            result shouldBe data
        }
    }

    describe("list") {
        it("prefix로 파일 목록을 조회한다") {
            val objects = listOf(
                S3Object.builder().key("prefix/a.parquet").build(),
                S3Object.builder().key("prefix/b.parquet").build(),
            )
            every { s3Client.listObjectsV2(any<ListObjectsV2Request>()) } returns
                ListObjectsV2Response.builder()
                    .contents(objects)
                    .isTruncated(false)
                    .build()

            val result = adapter.list("prefix/")
            result shouldBe listOf("prefix/a.parquet", "prefix/b.parquet")
        }
    }

    describe("delete") {
        it("S3에서 파일을 삭제한다") {
            every { s3Client.deleteObject(any<DeleteObjectRequest>()) } returns
                DeleteObjectResponse.builder().build()

            adapter.delete("test/file.parquet")

            verify {
                s3Client.deleteObject(
                    match<DeleteObjectRequest> {
                        it.bucket() == bucket && it.key() == "test/file.parquet"
                    },
                )
            }
        }
    }

    describe("exists") {
        it("존재하는 파일은 true를 반환한다") {
            every { s3Client.headObject(any<HeadObjectRequest>()) } returns
                HeadObjectResponse.builder().build()

            adapter.exists("test/file.parquet") shouldBe true
        }

        it("존재하지 않는 파일은 false를 반환한다") {
            every { s3Client.headObject(any<HeadObjectRequest>()) } throws
                NoSuchKeyException.builder().message("Not found").build()

            adapter.exists("nonexistent.parquet") shouldBe false
        }
    }

    describe("getUri") {
        it("s3:// URI를 반환한다") {
            val uri = adapter.getUri("test/file.parquet")
            uri shouldBe "s3://test-bucket/test/file.parquet"
        }
    }

    describe("testConnection") {
        it("버킷 접근이 가능하면 true를 반환한다") {
            every { s3Client.headBucket(any<HeadBucketRequest>()) } returns
                HeadBucketResponse.builder().build()

            adapter.testConnection() shouldBe true
        }

        it("버킷 접근이 불가능하면 false를 반환한다") {
            every { s3Client.headBucket(any<HeadBucketRequest>()) } throws
                NoSuchBucketException.builder().message("No bucket").build()

            adapter.testConnection() shouldBe false
        }
    }
})
