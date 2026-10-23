package com.autoscientist.dto.dataset;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Explicit type overrides applied during re-profiling.
 * Lets a user include an automatically-detected identifier in analysis, or force a
 * column to be treated as an identifier. The uploaded file is never modified.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReprofileRequestDto {
    /** Column names to force to IDENTIFIER. */
    private List<String> forceIdentifier;
    /** Column names to stop treating as IDENTIFIER (explicit inclusion). */
    private List<String> forceNonIdentifier;
}
