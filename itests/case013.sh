#!/usr/bin/env bash

set -euxo pipefail

mixcr align --preset test-airr-long-ig-base -f \
      --species hs \
      -OvParameters.geneFeatureToAlign="{FR1Begin:VEnd}" \
      long_ig_R1.fastq \
      long_ig_R2.fastq \
      case13_full.vdjca

mixcr align --preset test-airr-long-ig-base -f \
      --species hs \
      -OvParameters.geneFeatureToAlign="{CDR1Begin:VEnd}" \
      long_ig_R1.fastq \
      long_ig_R2.fastq \
      case13_cut.vdjca

mixcr exportAirr --imgt-gaps case13_full.vdjca case13_full.vdjca.imgt.airr.tsv
mixcr exportAirr --imgt-gaps case13_cut.vdjca case13_cut.vdjca.imgt.airr.tsv

mixcr assemble -a -OassemblingFeatures="{FR1Begin:FR4End}" \
      case13_full.vdjca \
      case13_full.clna

mixcr assemble -a -OassemblingFeatures="{CDR1Begin:FR4End}" \
      case13_cut.vdjca \
      case13_cut.clna

mixcr exportAirr --imgt-gaps case13_full.clna case13_full.clna.imgt.airr.tsv
mixcr exportAirr --imgt-gaps case13_cut.clna case13_cut.clna.imgt.airr.tsv

# Assertions

[[ $(cat case13_full.vdjca.imgt.airr.tsv | wc -l) -eq 2 ]] || exit 1
[[ $(cat case13_cut.vdjca.imgt.airr.tsv | wc -l) -eq 2 ]] || exit 1
[[ $(cat case13_full.clna.imgt.airr.tsv | wc -l) -eq 2 ]] || exit 1
[[ $(cat case13_cut.clna.imgt.airr.tsv | wc -l) -eq 2 ]] || exit 1

cmp <(cut -f 9,10 case13_full.vdjca.imgt.airr.tsv) <(cut -f 9,10 case13_full.clna.imgt.airr.tsv)
cmp <(cut -f 9,10 case13_cut.vdjca.imgt.airr.tsv) <(cut -f 9,10 case13_cut.clna.imgt.airr.tsv)

cmp <(cut -f 10 case13_full.vdjca.imgt.airr.tsv) <(cut -f 10 case13_cut.vdjca.imgt.airr.tsv)
cmp <(cut -f 9 case13_full.vdjca.imgt.airr.tsv | cut -c 79-) <(cut -f 9 case13_cut.vdjca.imgt.airr.tsv | cut -c 79-)
