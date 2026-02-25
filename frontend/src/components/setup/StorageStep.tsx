import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group"
import { api } from "@/lib/api"
import { CheckCircle, Loader2, XCircle } from "lucide-react"

export interface StorageData {
  storageType: "LOCAL" | "S3"
  localPath: string
  s3Bucket: string
  s3Region: string
  s3Endpoint: string
  s3AccessKey: string
  s3SecretKey: string
}

interface StorageStepProps {
  data: StorageData
  onChange: (data: StorageData) => void
  onNext: () => void
  onBack: () => void
}

export function StorageStep({ data, onChange, onNext, onBack }: StorageStepProps) {
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [testStatus, setTestStatus] = useState<"idle" | "testing" | "success" | "error">("idle")

  function validate(): boolean {
    const newErrors: Record<string, string> = {}

    if (data.storageType === "LOCAL") {
      if (!data.localPath.trim()) {
        newErrors.localPath = "Path is required"
      }
    } else {
      if (!data.s3Bucket.trim()) {
        newErrors.s3Bucket = "Bucket name is required"
      }
      if (!data.s3Region.trim()) {
        newErrors.s3Region = "Region is required"
      }
    }

    setErrors(newErrors)
    return Object.keys(newErrors).length === 0
  }

  async function handleTest() {
    if (!validate()) return

    setTestStatus("testing")
    try {
      const res = await api.post<{ success: boolean }>("/api/setup/test-storage", {
        storageType: data.storageType,
        localPath: data.localPath || null,
        s3Bucket: data.s3Bucket || null,
        s3Region: data.s3Region || null,
        s3Endpoint: data.s3Endpoint || null,
        s3AccessKey: data.s3AccessKey || null,
        s3SecretKey: data.s3SecretKey || null,
      })
      setTestStatus(res.success ? "success" : "error")
    } catch {
      setTestStatus("error")
    }
  }

  function handleNext() {
    if (validate()) {
      onNext()
    }
  }

  return (
    <div className="space-y-8">
      <div className="space-y-2">
        <h2 className="text-2xl font-bold tracking-tight">Storage Settings</h2>
        <p className="text-muted-foreground">
          Configure where Snaplake stores database snapshots.
        </p>
      </div>

      <RadioGroup
        value={data.storageType}
        onValueChange={(value) =>
          onChange({ ...data, storageType: value as "LOCAL" | "S3" })
        }
        className="space-y-3"
      >
        <label className="flex cursor-pointer items-center gap-3 rounded-xl border p-4 hover:bg-accent">
          <RadioGroupItem value="LOCAL" />
          <div>
            <p className="font-medium">Local Filesystem</p>
            <p className="text-sm text-muted-foreground">
              Store snapshots on the local disk
            </p>
          </div>
        </label>
        <label className="flex cursor-pointer items-center gap-3 rounded-xl border p-4 hover:bg-accent">
          <RadioGroupItem value="S3" />
          <div>
            <p className="font-medium">S3 Compatible Storage</p>
            <p className="text-sm text-muted-foreground">
              AWS S3, MinIO, or any S3-compatible storage
            </p>
          </div>
        </label>
      </RadioGroup>

      <div className="space-y-4">
        {data.storageType === "LOCAL" ? (
          <div className="space-y-2">
            <Label htmlFor="localPath">Storage Path</Label>
            <Input
              id="localPath"
              value={data.localPath}
              onChange={(e) => onChange({ ...data, localPath: e.target.value })}
              placeholder="/data/snaplake"
            />
            {errors.localPath && (
              <p className="text-sm text-destructive">{errors.localPath}</p>
            )}
          </div>
        ) : (
          <>
            <div className="space-y-2">
              <Label htmlFor="s3Bucket">Bucket Name</Label>
              <Input
                id="s3Bucket"
                value={data.s3Bucket}
                onChange={(e) =>
                  onChange({ ...data, s3Bucket: e.target.value })
                }
                placeholder="my-snaplake-bucket"
              />
              {errors.s3Bucket && (
                <p className="text-sm text-destructive">{errors.s3Bucket}</p>
              )}
            </div>
            <div className="space-y-2">
              <Label htmlFor="s3Region">Region</Label>
              <Input
                id="s3Region"
                value={data.s3Region}
                onChange={(e) =>
                  onChange({ ...data, s3Region: e.target.value })
                }
                placeholder="us-east-1"
              />
              {errors.s3Region && (
                <p className="text-sm text-destructive">{errors.s3Region}</p>
              )}
            </div>
            <div className="space-y-2">
              <Label htmlFor="s3Endpoint">
                Custom Endpoint{" "}
                <span className="text-muted-foreground">(optional)</span>
              </Label>
              <Input
                id="s3Endpoint"
                value={data.s3Endpoint}
                onChange={(e) =>
                  onChange({ ...data, s3Endpoint: e.target.value })
                }
                placeholder="https://minio.example.com"
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="s3AccessKey">Access Key</Label>
              <Input
                id="s3AccessKey"
                value={data.s3AccessKey}
                onChange={(e) =>
                  onChange({ ...data, s3AccessKey: e.target.value })
                }
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="s3SecretKey">Secret Key</Label>
              <Input
                id="s3SecretKey"
                type="password"
                value={data.s3SecretKey}
                onChange={(e) =>
                  onChange({ ...data, s3SecretKey: e.target.value })
                }
              />
            </div>
          </>
        )}
      </div>

      <div className="flex items-center gap-3">
        <Button
          variant="outline"
          onClick={handleTest}
          disabled={testStatus === "testing"}
        >
          {testStatus === "testing" && (
            <Loader2 className="mr-2 h-4 w-4 animate-spin" />
          )}
          Test Connection
        </Button>
        {testStatus === "success" && (
          <span className="flex items-center gap-1 text-sm text-green-600">
            <CheckCircle className="h-4 w-4" /> Connected
          </span>
        )}
        {testStatus === "error" && (
          <span className="flex items-center gap-1 text-sm text-destructive">
            <XCircle className="h-4 w-4" /> Connection failed
          </span>
        )}
      </div>

      <div className="flex gap-4">
        <Button variant="outline" onClick={onBack} className="flex-1 h-11">
          Back
        </Button>
        <Button onClick={handleNext} className="flex-1 h-11">
          Next
        </Button>
      </div>
    </div>
  )
}
