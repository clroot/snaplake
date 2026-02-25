import { Button } from "@/components/ui/button"
import { Database } from "lucide-react"

interface WelcomeStepProps {
  onNext: () => void
}

export function WelcomeStep({ onNext }: WelcomeStepProps) {
  return (
    <div className="flex flex-col items-center space-y-8 text-center">
      <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-primary">
        <Database className="h-8 w-8 text-primary-foreground" />
      </div>
      <div className="space-y-2">
        <h1 className="text-3xl font-bold tracking-tight">
          Welcome to Snaplake
        </h1>
        <p className="max-w-prose text-muted-foreground">
          Your self-hosted database snapshot management platform. Let's get you
          set up in a few quick steps.
        </p>
      </div>
      <Button onClick={onNext} className="w-full h-14 text-base sm:w-auto sm:h-11 sm:px-8">
        Get Started
      </Button>
    </div>
  )
}
