import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { api } from "@/lib/api"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group"
import { Badge } from "@/components/ui/badge"
import { Skeleton } from "@/components/ui/skeleton"
import { AlertCircle, CheckCircle, Loader2, XCircle } from "lucide-react"

interface StorageSettings {
  type: string
  localPath: string | null
  s3Bucket: string | null
  s3Region: string | null
  s3Endpoint: string | null
  s3AccessKey: string | null
  s3SecretKey: string | null
}

interface StorageTestResponse {
  success: boolean
}

export function StorageSettingsPage() {
  const queryClient = useQueryClient()
  const [isEditing, setIsEditing] = useState(false)

  const { data: settings, isLoading } = useQuery({
    queryKey: ["storage-settings"],
    queryFn: () => api.get<StorageSettings>("/api/storage"),
  })

  const [formData, setFormData] = useState<StorageSettings | null>(null)

  const [testStatus, setTestStatus] = useState<
    "idle" | "testing" | "success" | "error"
  >("idle")

  const updateMutation = useMutation({
    mutationFn: (data: StorageSettings) =>
      api.put("/api/storage", {
        storageType: data.type,
        localPath: data.type === "LOCAL" ? data.localPath : null,
        s3Bucket: data.type === "S3" ? data.s3Bucket : null,
        s3Region: data.type === "S3" ? data.s3Region : null,
        s3Endpoint: data.type === "S3" ? data.s3Endpoint : null,
        s3AccessKey: data.type === "S3" ? data.s3AccessKey : null,
        s3SecretKey: data.type === "S3" ? data.s3SecretKey : null,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["storage-settings"] })
      setIsEditing(false)
    },
  })

  async function handleTest() {
    setTestStatus("testing")
    try {
      const result = await api.post<StorageTestResponse>("/api/storage/test")
      setTestStatus(result.success ? "success" : "error")
    } catch {
      setTestStatus("error")
    }
  }

  function startEditing() {
    if (settings) {
      setFormData({
        ...settings,
        s3AccessKey: "",
        s3SecretKey: "",
      })
    }
    setIsEditing(true)
  }

  function handleSave() {
    if (formData) {
      updateMutation.mutate(formData)
    }
  }

  if (isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-48" />
      </div>
    )
  }

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Storage Settings</h1>
        <p className="text-muted-foreground">
          Configure where Snaplake stores database snapshots
        </p>
      </div>

      {!isEditing ? (
        /* View mode */
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <CardTitle>Current Configuration</CardTitle>
              <div className="flex gap-2">
                <Button variant="outline" size="sm" onClick={handleTest}>
                  {testStatus === "testing" && (
                    <Loader2 className="mr-1 h-3 w-3 animate-spin" />
                  )}
                  Test Connection
                </Button>
                <Button size="sm" onClick={startEditing}>
                  Edit
                </Button>
              </div>
            </div>
            <CardDescription>
              {testStatus === "success" && (
                <span className="flex items-center gap-1 text-green-600">
                  <CheckCircle className="h-4 w-4" /> Connection successful
                </span>
              )}
              {testStatus === "error" && (
                <span className="flex items-center gap-1 text-destructive">
                  <XCircle className="h-4 w-4" /> Connection failed
                </span>
              )}
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="flex items-center gap-3">
              <Label className="w-32 text-muted-foreground">Type</Label>
              <Badge variant="outline">{settings?.type}</Badge>
            </div>
            {settings?.type === "LOCAL" && (
              <div className="flex items-center gap-3">
                <Label className="w-32 text-muted-foreground">Path</Label>
                <code className="rounded bg-muted px-2 py-1 text-sm">
                  {settings.localPath}
                </code>
              </div>
            )}
            {settings?.type === "S3" && (
              <>
                <div className="flex items-center gap-3">
                  <Label className="w-32 text-muted-foreground">Bucket</Label>
                  <span>{settings.s3Bucket}</span>
                </div>
                <div className="flex items-center gap-3">
                  <Label className="w-32 text-muted-foreground">Region</Label>
                  <span>{settings.s3Region}</span>
                </div>
                {settings.s3Endpoint && (
                  <div className="flex items-center gap-3">
                    <Label className="w-32 text-muted-foreground">
                      Endpoint
                    </Label>
                    <span>{settings.s3Endpoint}</span>
                  </div>
                )}
                <div className="flex items-center gap-3">
                  <Label className="w-32 text-muted-foreground">
                    Access Key
                  </Label>
                  <code className="rounded bg-muted px-2 py-1 text-sm">
                    {settings.s3AccessKey}
                  </code>
                </div>
              </>
            )}
          </CardContent>
        </Card>
      ) : (
        /* Edit mode */
        <Card>
          <CardHeader>
            <CardTitle>Edit Configuration</CardTitle>
          </CardHeader>
          <CardContent className="space-y-6">
            {formData && (
              <>
                {updateMutation.isError && (
                  <div className="flex items-center gap-2 rounded-lg border border-destructive/50 bg-destructive/10 p-3 text-sm text-destructive">
                    <AlertCircle className="h-4 w-4 shrink-0" />
                    {updateMutation.error instanceof Error
                      ? updateMutation.error.message
                      : "Failed to update storage settings"}
                  </div>
                )}

                <RadioGroup
                  value={formData.type}
                  onValueChange={(value) =>
                    setFormData({ ...formData, type: value })
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
                  {formData.type === "LOCAL" ? (
                    <div className="space-y-2">
                      <Label>Storage Path</Label>
                      <Input
                        value={formData.localPath ?? ""}
                        onChange={(e) =>
                          setFormData({
                            ...formData,
                            localPath: e.target.value,
                          })
                        }
                        placeholder="/data/snaplake"
                      />
                    </div>
                  ) : (
                    <>
                      <div className="space-y-2">
                        <Label>Bucket Name</Label>
                        <Input
                          value={formData.s3Bucket ?? ""}
                          onChange={(e) =>
                            setFormData({
                              ...formData,
                              s3Bucket: e.target.value,
                            })
                          }
                        />
                      </div>
                      <div className="space-y-2">
                        <Label>Region</Label>
                        <Input
                          value={formData.s3Region ?? ""}
                          onChange={(e) =>
                            setFormData({
                              ...formData,
                              s3Region: e.target.value,
                            })
                          }
                        />
                      </div>
                      <div className="space-y-2">
                        <Label>
                          Custom Endpoint{" "}
                          <span className="text-muted-foreground">
                            (optional)
                          </span>
                        </Label>
                        <Input
                          value={formData.s3Endpoint ?? ""}
                          onChange={(e) =>
                            setFormData({
                              ...formData,
                              s3Endpoint: e.target.value,
                            })
                          }
                        />
                      </div>
                      <div className="space-y-2">
                        <Label>Access Key</Label>
                        <Input
                          value={formData.s3AccessKey ?? ""}
                          onChange={(e) =>
                            setFormData({
                              ...formData,
                              s3AccessKey: e.target.value,
                            })
                          }
                          placeholder="Leave empty to keep current value"
                        />
                      </div>
                      <div className="space-y-2">
                        <Label>Secret Key</Label>
                        <Input
                          type="password"
                          value={formData.s3SecretKey ?? ""}
                          onChange={(e) =>
                            setFormData({
                              ...formData,
                              s3SecretKey: e.target.value,
                            })
                          }
                          placeholder="Leave empty to keep current value"
                        />
                      </div>
                    </>
                  )}
                </div>

                <div className="flex gap-4">
                  <Button
                    variant="outline"
                    onClick={() => setIsEditing(false)}
                    className="flex-1 h-11"
                  >
                    Cancel
                  </Button>
                  <Button
                    onClick={handleSave}
                    disabled={updateMutation.isPending}
                    className="flex-1 h-11"
                  >
                    {updateMutation.isPending && (
                      <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    )}
                    Save Changes
                  </Button>
                </div>
              </>
            )}
          </CardContent>
        </Card>
      )}
    </div>
  )
}
