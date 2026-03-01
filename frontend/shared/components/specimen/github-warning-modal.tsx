"use client";

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from "@/shared/components/ui/dialog";
import { Button } from "@/shared/components/ui/button";
import { AlertTriangle, ExternalLink } from "lucide-react";

interface GitHubWarningModalProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => void;
  repoUrl: string;
  warningTitle?: string;
  warningBody?: string;
  destinationLabel?: string;
  confirmLabel?: string;
  cancelLabel?: string;
  closeLabel?: string;
}

/**
 * GitHubWarningModal
 * A stylized warning modal before leaving the app for GitHub.
 */
export function GitHubWarningModal({
  isOpen,
  onClose,
  onConfirm,
  repoUrl,
  warningTitle = "Warning: high radiation zone",
  warningBody = "You are about to enter a GitHub repository. Protective gear (and a healthy skepticism) is recommended beyond this point.",
  destinationLabel = "Target destination",
  confirmLabel = "Proceed to GitHub",
  cancelLabel = "Cancel",
  closeLabel = "Close dialog",
}: GitHubWarningModalProps) {
  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && onClose()}>
      <DialogContent
        closeLabel={closeLabel}
        className="sm:max-w-[425px] bg-zinc-950 border-primary/20 shadow-2xl shadow-primary/10"
      >
        <DialogHeader>
          <div className="mx-auto w-12 h-12 rounded-full bg-primary/10 flex items-center justify-center mb-4">
            <AlertTriangle className="w-6 h-6 text-primary animate-pulse" />
          </div>
          <DialogTitle className="text-center font-mono text-primary tracking-tighter text-xl">
            {warningTitle}
          </DialogTitle>
          <DialogDescription className="text-center font-mono text-zinc-500 text-xs py-2">
            {warningBody}
          </DialogDescription>
        </DialogHeader>

        <div className="py-4 px-6 bg-black/40 border border-white/5 rounded-lg">
          <p className="text-[10px] font-mono text-zinc-600 mb-1 tracking-widest">
            {destinationLabel}
          </p>
          <p className="text-sm font-mono text-zinc-300 truncate">
            {repoUrl}
          </p>
        </div>

        <DialogFooter className="flex-col sm:flex-col gap-2 mt-4">
          <Button
            onClick={onConfirm}
            className="w-full font-mono group"
          >
            {confirmLabel}
            <ExternalLink className="w-3 h-3 ml-2 group-hover:translate-x-0.5 group-hover:-translate-y-0.5 transition-transform" />
          </Button>
          <Button
            variant="ghost"
            onClick={onClose}
            className="w-full font-mono text-zinc-500 hover:text-zinc-300"
          >
            {cancelLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
