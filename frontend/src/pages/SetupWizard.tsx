import { useState } from "react"
import { useNavigate } from "@tanstack/react-router"
import { WelcomeStep } from "@/components/setup/WelcomeStep"
import { AdminAccountStep } from "@/components/setup/AdminAccountStep"
import { StorageStep } from "@/components/setup/StorageStep"
import type { StorageData } from "@/components/setup/StorageStep"
import { DatasourceStep } from "@/components/setup/DatasourceStep"
import type { DatasourceData } from "@/components/setup/DatasourceStep"
import { CompleteStep } from "@/components/setup/CompleteStep"
import { api } from "@/lib/api"
import { setAuth } from "@/lib/auth"
import { cn } from "@/lib/utils"

const STEPS = ["Welcome", "Account", "Storage", "Datasource", "Complete"]

export function SetupWizard() {
  const navigate = useNavigate()
  const [step, setStep] = useState(0)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [adminData, setAdminData] = useState({
    username: "",
    password: "",
    confirmPassword: "",
  })

  const [storageData, setStorageData] = useState<StorageData>({
    storageType: "LOCAL",
    localPath: "./data/snapshots",
    s3Bucket: "",
    s3Region: "",
    s3Endpoint: "",
    s3AccessKey: "",
    s3SecretKey: "",
  })

  const [datasourceData, setDatasourceData] = useState<DatasourceData>({
    skip: true,
    name: "",
    type: "POSTGRESQL",
    host: "",
    port: "5432",
    database: "",
    username: "",
    password: "",
    schemas: "public",
  })

  async function handleFinish() {
    setIsSubmitting(true)
    setError(null)

    try {
      // Initialize the system
      await api.post("/api/setup/initialize", {
        adminUsername: adminData.username,
        adminPassword: adminData.password,
        storageType: storageData.storageType,
        localPath:
          storageData.storageType === "LOCAL" ? storageData.localPath : null,
        s3Bucket:
          storageData.storageType === "S3" ? storageData.s3Bucket : null,
        s3Region:
          storageData.storageType === "S3" ? storageData.s3Region : null,
        s3Endpoint:
          storageData.storageType === "S3" && storageData.s3Endpoint
            ? storageData.s3Endpoint
            : null,
        s3AccessKey:
          storageData.storageType === "S3" ? storageData.s3AccessKey : null,
        s3SecretKey:
          storageData.storageType === "S3" ? storageData.s3SecretKey : null,
      })

      // Auto-login after setup
      const loginResult = await api.post<{ token: string; expiresAt: string }>(
        "/api/auth/login",
        {
          username: adminData.username,
          password: adminData.password,
        },
      )
      setAuth(loginResult.token, loginResult.expiresAt, adminData.username)

      // Register first datasource if not skipped
      if (!datasourceData.skip && datasourceData.name) {
        await api.post("/api/datasources", {
          name: datasourceData.name,
          type: datasourceData.type,
          host: datasourceData.host,
          port: parseInt(datasourceData.port, 10),
          database: datasourceData.database,
          username: datasourceData.username,
          password: datasourceData.password,
          schemas: datasourceData.schemas
            .split(",")
            .map((s) => s.trim())
            .filter(Boolean),
        })
      }

      navigate({ to: "/" })
    } catch (err) {
      // If initialization succeeded but login failed, redirect to login
      try {
        const status = await api.get<{ initialized: boolean }>("/api/setup/status")
        if (status.initialized) {
          navigate({ to: "/login" })
          return
        }
      } catch {
        // ignore
      }
      setError(
        err instanceof Error ? err.message : "Setup failed. Please try again.",
      )
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-background p-4">
      <title>Setup - Snaplake</title>
      <div className="w-full max-w-lg space-y-8">
        {/* Progress indicator */}
        <div className="flex justify-center gap-2">
          {STEPS.map((_, i) => (
            <div
              key={i}
              className={cn(
                "h-2 w-8 rounded-full transition-colors",
                i <= step ? "bg-primary" : "bg-muted",
              )}
            />
          ))}
        </div>

        {/* Step content */}
        {step === 0 && <WelcomeStep onNext={() => setStep(1)} />}
        {step === 1 && (
          <AdminAccountStep
            data={adminData}
            onChange={setAdminData}
            onNext={() => setStep(2)}
            onBack={() => setStep(0)}
          />
        )}
        {step === 2 && (
          <StorageStep
            data={storageData}
            onChange={setStorageData}
            onNext={() => setStep(3)}
            onBack={() => setStep(1)}
          />
        )}
        {step === 3 && (
          <DatasourceStep
            data={datasourceData}
            onChange={setDatasourceData}
            onNext={() => setStep(4)}
            onBack={() => setStep(2)}
          />
        )}
        {step === 4 && (
          <CompleteStep
            isSubmitting={isSubmitting}
            error={error}
            onFinish={handleFinish}
            onBack={() => setStep(3)}
          />
        )}
      </div>
    </div>
  )
}
