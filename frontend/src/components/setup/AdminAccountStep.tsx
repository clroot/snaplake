import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"

interface AdminAccountStepProps {
  data: { username: string; password: string; confirmPassword: string }
  onChange: (data: { username: string; password: string; confirmPassword: string }) => void
  onNext: () => void
  onBack: () => void
}

export function AdminAccountStep({
  data,
  onChange,
  onNext,
  onBack,
}: AdminAccountStepProps) {
  const [errors, setErrors] = useState<Record<string, string>>({})

  function validate(): boolean {
    const newErrors: Record<string, string> = {}

    if (!data.username.trim()) {
      newErrors.username = "Username is required"
    } else if (data.username.length < 3) {
      newErrors.username = "Username must be at least 3 characters"
    }

    if (!data.password) {
      newErrors.password = "Password is required"
    } else if (data.password.length < 8) {
      newErrors.password = "Password must be at least 8 characters"
    }

    if (data.password !== data.confirmPassword) {
      newErrors.confirmPassword = "Passwords do not match"
    }

    setErrors(newErrors)
    return Object.keys(newErrors).length === 0
  }

  function handleNext() {
    if (validate()) {
      onNext()
    }
  }

  return (
    <div className="space-y-8">
      <div className="space-y-2">
        <h2 className="text-2xl font-bold tracking-tight">Admin Account</h2>
        <p className="text-muted-foreground">
          Create the admin account for managing Snaplake.
        </p>
      </div>
      <div className="space-y-4">
        <div className="space-y-2">
          <Label htmlFor="username">Username</Label>
          <Input
            id="username"
            value={data.username}
            onChange={(e) => onChange({ ...data, username: e.target.value })}
            placeholder="admin"
          />
          {errors.username && (
            <p className="text-sm text-destructive">{errors.username}</p>
          )}
        </div>
        <div className="space-y-2">
          <Label htmlFor="password">Password</Label>
          <Input
            id="password"
            type="password"
            value={data.password}
            onChange={(e) => onChange({ ...data, password: e.target.value })}
            placeholder="At least 8 characters"
          />
          {errors.password && (
            <p className="text-sm text-destructive">{errors.password}</p>
          )}
        </div>
        <div className="space-y-2">
          <Label htmlFor="confirmPassword">Confirm Password</Label>
          <Input
            id="confirmPassword"
            type="password"
            value={data.confirmPassword}
            onChange={(e) =>
              onChange({ ...data, confirmPassword: e.target.value })
            }
            placeholder="Re-enter your password"
          />
          {errors.confirmPassword && (
            <p className="text-sm text-destructive">{errors.confirmPassword}</p>
          )}
        </div>
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
