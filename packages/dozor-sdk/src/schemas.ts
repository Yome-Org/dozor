import { z } from "zod";

export const DozorSeveritySchema = z.enum(["INFO", "WARNING", "CRITICAL"]);

export const DozorSignalRequestSchema = z.object({
  component: z.string().min(1),
  severity: DozorSeveritySchema,
  source: z.string().min(1),
  occurredAt: z.string().datetime({ offset: true }),
  idempotencyKey: z.string().min(1).optional(),
});

export const DozorAcceptedResponseSchema = z.object({
  status: z.enum(["accepted", "duplicate"]),
  queueUtilization: z.number(),
});

export const DozorErrorResponseSchema = z.object({
  code: z.string().min(1),
  message: z.string().min(1),
  details: z.string().nullable().optional(),
});
