import { Button } from "@/components/ui/button"
import { CheckCircle, Loader2 } from "lucide-react"

interface CompleteStepProps {
  isSubmitting: boolean
  error: string | null
  onFinish: () => void
  onBack: () => void
}

export function CompleteStep({
  isSubmitting,
  error,
  onFinish,
  onBack,
}: CompleteStepProps) {
  return (
    <div className="flex flex-col items-center space-y-8 text-center">
      <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-green-100 dark:bg-green-900">
        <CheckCircle className="h-8 w-8 text-green-600 dark:text-green-400" />
      </div>
      <div className="space-y-2">
        <h2 className="text-2xl font-bold tracking-tight">Ready to Go</h2>
        <p className="max-w-prose text-muted-foreground">
          Everything is configured. Click the button below to initialize
          Snaplake and start managing your database snapshots.
        </p>
      </div>
      {error && (
        <p className="text-sm text-destructive">{error}</p>
      )}
      <div className="flex w-full gap-4 sm:w-auto">
        <Button
          variant="outline"
          onClick={onBack}
          disabled={isSubmitting}
          className="flex-1 h-11 sm:px-8"
        >
          Back
        </Button>
        <Button
          onClick={onFinish}
          disabled={isSubmitting}
          className="flex-1 h-14 text-base sm:h-11 sm:px-8"
        >
          {isSubmitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
          Complete Setup
        </Button>
      </div>
    </div>
  )
}
