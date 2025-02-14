/*
 * Copyright (c) 2014-2022, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
 */
package com.milaboratory.mixcr.basictypes;

import cc.redberry.primitives.Filter;
import cc.redberry.primitives.FilterUtil;
import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.AffineGapAlignmentScoring;
import com.milaboratory.core.alignment.Aligner;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.alignment.MultiAlignmentHelper;
import com.milaboratory.core.sequence.AminoAcidAlphabet;
import com.milaboratory.core.sequence.AminoAcidSequence;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import io.repseq.core.GeneType;
import io.repseq.core.RangeTranslationParameters;
import io.repseq.core.ReferencePoint;
import io.repseq.core.SequencePartitioning;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class VDJCAlignmentsFormatter {
    public static MultiAlignmentHelper getTargetAsMultiAlignment(VDJCObject vdjcObject, int targetId) {
        return getTargetAsMultiAlignment(vdjcObject, targetId, false,
                vdjcObject instanceof VDJCAlignments && ((VDJCAlignments) vdjcObject).getOriginalReads() != null);
    }

    public static MultiAlignmentHelper getTargetAsMultiAlignment(VDJCObject vdjcObject, int targetId,
                                                                 boolean addHitScore, boolean addReads) {
        if (addReads && !(vdjcObject instanceof VDJCAlignments))
            throw new IllegalArgumentException("Read alignments supported only for VDJCAlignments.");

        NSequenceWithQuality target = vdjcObject.getTarget(targetId);
        NucleotideSequence targetSeq = target.getSequence();
        SequencePartitioning partitioning = vdjcObject.getPartitionedTarget(targetId).getPartitioning();

        List<Alignment<NucleotideSequence>> alignments = new ArrayList<>();
        List<String> alignmentLeftComments = new ArrayList<>();
        List<String> alignmentRightComments = new ArrayList<>();
        for (GeneType gt : GeneType.values())
            for (VDJCHit hit : vdjcObject.getHits(gt)) {
                Alignment<NucleotideSequence> alignment = hit.getAlignment(targetId);
                if (alignment == null)
                    continue;
                alignment = alignment.invert(targetSeq);
                alignments.add(alignment);
                alignmentLeftComments.add(hit.getGene().getName());
                alignmentRightComments.add(" " + (int) (hit.getAlignment(targetId).getScore()) + (addHitScore ? " (" + (int) (hit.getScore()) + ")" : ""));
            }

        // Adding read information
        if (addReads) {
            VDJCAlignments vdjcAlignments = (VDJCAlignments) vdjcObject;
            SequenceHistory history = vdjcAlignments.getHistory(targetId);
            List<SequenceHistory.RawSequence> reads = history.rawReads();
            for (SequenceHistory.RawSequence read : reads) {
                NucleotideSequence seq = vdjcAlignments.getOriginalSequence(read.index).getSequence();
                int offset = history.offset(read.index);
                Alignment<NucleotideSequence> alignment = Aligner.alignOnlySubstitutions(targetSeq, seq, offset, seq.size(), 0, seq.size(),
                        AffineGapAlignmentScoring.IGBLAST_NUCLEOTIDE_SCORING);
                alignments.add(alignment);
                alignmentLeftComments.add(read.index.toString());
                alignmentRightComments.add("");
            }
        }

        MultiAlignmentHelper helper = MultiAlignmentHelper.build(MultiAlignmentHelper.DEFAULT_SETTINGS,
                new Range(0, target.size()), targetSeq, alignments.toArray(new Alignment[alignments.size()]));

        if (!alignments.isEmpty())
            drawPoints(helper, partitioning, POINTS_FOR_REARRANGED);

        drawAASequence(helper, partitioning, targetSeq);

        helper.addSubjectQuality("Quality", target.getQuality());
        helper.setSubjectLeftTitle("Target" + targetId);
        helper.setSubjectRightTitle(" Score" + (addHitScore ? " (hit score)" : ""));
        for (int i = 0; i < alignmentLeftComments.size(); i++) {
            helper.setQueryLeftTitle(i, alignmentLeftComments.get(i));
            helper.setQueryRightTitle(i, alignmentRightComments.get(i));
        }
        return helper;
    }

    public static void drawAASequence(MultiAlignmentHelper helper, SequencePartitioning partitioning,
                                      NucleotideSequence target) {
        List<RangeTranslationParameters> trParams = partitioning.getTranslationParameters(target.size());
        char[] line = new char[helper.size()];
        Arrays.fill(line, ' ');
        for (RangeTranslationParameters trParam : trParams) {
            NucleotideSequence mainSequence = target.getRange(trParam.range);
            NucleotideSequence leftover = trParam.codonLeftoverRange == null
                    ? null
                    : target.getRange(trParam.codonLeftoverRange);
            NucleotideSequence bigSeq = leftover == null ? mainSequence :
                    trParam.leftIncompleteCodonRange() != null
                            ? leftover.concatenate(mainSequence)
                            : mainSequence.concatenate(leftover);
            AminoAcidSequence aa = AminoAcidSequence.translate(bigSeq,
                    trParam.translationParameters);
            int aaPosition = 0;
            int ntPosition = trParam.range.getFrom()
                    + AminoAcidSequence.convertAAPositionToNt(aaPosition, mainSequence.size(),
                    trParam.translationParameters);

            if (aa.codeAt(aaPosition) == AminoAcidAlphabet.INCOMPLETE_CODON) {
                line[helper.subjectToAlignmentPosition(ntPosition)] =
                        AminoAcidSequence.ALPHABET.codeToSymbol(aa.codeAt(aaPosition)); // '_'
                ++aaPosition;
            }

            while (aaPosition < aa.size() &&
                    (aaPosition < aa.size() - 1 || aa.codeAt(aaPosition) != AminoAcidAlphabet.INCOMPLETE_CODON)) {

                ntPosition = trParam.range.getFrom()
                        + AminoAcidSequence.convertAAPositionToNt(aaPosition, bigSeq.size(),
                        trParam.translationParameters);

                if (leftover != null && trParam.leftIncompleteCodonRange() != null)
                    ntPosition -= trParam.leftIncompleteCodonRange().length();
                boolean isLeftover = false;
                if (leftover != null) {
                    if (trParam.leftIncompleteCodonRange() != null)
                        isLeftover = (aaPosition == 0);
                    else
                        isLeftover = (aaPosition == aa.size() - 1);
                }

                if (aa.codeAt(aaPosition) != AminoAcidAlphabet.INCOMPLETE_CODON)
                    ++ntPosition;
                char c = AminoAcidSequence.ALPHABET.codeToSymbol(aa.codeAt(aaPosition));
                if (isLeftover)
                    c = Character.toLowerCase(c);
                line[helper.subjectToAlignmentPosition(ntPosition)] = c;
                ++aaPosition;
            }
            if (aaPosition < aa.size() && (aaPosition < aa.size() - 1 || aa.codeAt(aaPosition) == AminoAcidAlphabet.INCOMPLETE_CODON)) {
                ntPosition = trParam.range.getFrom()
                        + AminoAcidSequence.convertAAPositionToNt(aaPosition, mainSequence.size(),
                        trParam.translationParameters);
                line[helper.subjectToAlignmentPosition(ntPosition)] =
                        AminoAcidSequence.ALPHABET.codeToSymbol(aa.codeAt(aaPosition));
            }
        }
        helper.addAnnotationString("", new String(line));
    }

    public static void drawPoints(MultiAlignmentHelper helper, SequencePartitioning partitioning,
                                  PointToDraw... pointsToDraw) {
        ArrayList<char[]> markers = new ArrayList<>();
        markers.add(emptyLine(helper.size()));
        boolean result;
        int i;
        for (PointToDraw point : pointsToDraw) {
            i = 0;
            do {
                if (markers.size() == i)
                    markers.add(emptyLine(helper.size()));
                result = point.draw(partitioning, helper, markers.get(i++), false);
            } while (!result);
        }
        for (i = markers.size() - 1; i >= 0; --i)
            helper.addAnnotationString("", new String(markers.get(i)));
    }

    private static char[] emptyLine(int size) {
        char[] markers = new char[size];
        Arrays.fill(markers, ' ');
        return markers;
    }

    public static final Filter<SequencePartitioning> IsVP = new Filter<SequencePartitioning>() {
        @Override
        public boolean accept(SequencePartitioning object) {
            return object.isAvailable(ReferencePoint.VEnd) && object.getPosition(ReferencePoint.VEnd) != object.getPosition(ReferencePoint.VEndTrimmed);
        }
    };
    public static final Filter<SequencePartitioning> IsJP = new Filter<SequencePartitioning>() {
        @Override
        public boolean accept(SequencePartitioning object) {
            return object.isAvailable(ReferencePoint.JBegin) && object.getPosition(ReferencePoint.JBegin) != object.getPosition(ReferencePoint.JBeginTrimmed);
        }
    };
    public static final Filter<SequencePartitioning> IsDPLeft = new Filter<SequencePartitioning>() {
        @Override
        public boolean accept(SequencePartitioning object) {
            return object.isAvailable(ReferencePoint.DBegin) && object.getPosition(ReferencePoint.DBegin) != object.getPosition(ReferencePoint.DBeginTrimmed);
        }
    };
    public static final Filter<SequencePartitioning> IsDPRight = new Filter<SequencePartitioning>() {
        @Override
        public boolean accept(SequencePartitioning object) {
            return object.isAvailable(ReferencePoint.DEnd) && object.getPosition(ReferencePoint.DEnd) != object.getPosition(ReferencePoint.DEndTrimmed);
        }
    };
    public static final Filter<SequencePartitioning> NotDPLeft = FilterUtil.not(IsDPLeft);
    public static final Filter<SequencePartitioning> NotDPRight = FilterUtil.not(IsDPRight);
    public static final Filter<SequencePartitioning> NotVP = FilterUtil.not(IsVP);
    public static final Filter<SequencePartitioning> NotJP = FilterUtil.not(IsJP);


    public static final PointToDraw[] POINTS_FOR_REARRANGED = new PointToDraw[]{
            pd(ReferencePoint.V5UTRBeginTrimmed, "<5'UTR"),
            pd(ReferencePoint.V5UTREnd, "5'UTR><L1"),
            pd(ReferencePoint.L1End, "L1>"),
            pd(ReferencePoint.L2Begin, "<L2"),
            pd(ReferencePoint.FR1Begin, "L2><FR1"),
            pd(ReferencePoint.CDR1Begin, "FR1><CDR1"),
            pd(ReferencePoint.FR2Begin, "CDR1><FR2"),
            pd(ReferencePoint.CDR2Begin, "FR2><CDR2"),
            pd(ReferencePoint.FR3Begin, "CDR2><FR3"),
            pd(ReferencePoint.CDR3Begin, "FR3><CDR3"),
            pd(ReferencePoint.VEndTrimmed, "V>", -1, NotVP),
            pd(ReferencePoint.VEnd, "V><VP", IsVP),
            pd(ReferencePoint.VEndTrimmed, "VP>", -1, IsVP),

            pd(ReferencePoint.DBeginTrimmed, "<D", NotDPLeft),
            pd(ReferencePoint.DBegin, "DP><D", IsDPLeft),
            pd(ReferencePoint.DBeginTrimmed, "<DP", IsDPLeft),

            pd(ReferencePoint.DEndTrimmed, "D>", -1, NotDPRight),
            pd(ReferencePoint.DEnd, "D><DP", IsDPRight),
            pd(ReferencePoint.DEndTrimmed, "DP>", IsDPRight),

            pd(ReferencePoint.JBeginTrimmed, "<J", NotJP),
            pd(ReferencePoint.JBegin, "JP><J", IsJP),
            pd(ReferencePoint.JBeginTrimmed, "<JP", IsJP),
            pd(ReferencePoint.CDR3End.move(-1), "CDR3><FR4").moveMarkerPoint(1),
            pd(ReferencePoint.FR4End, "FR4>", -1),
            pd(ReferencePoint.CBegin, "<C")
    };

    public static final PointToDraw[] POINTS_FOR_GERMLINE = new PointToDraw[]{
            pd(ReferencePoint.V5UTRBeginTrimmed, "<5'UTR"),
            pd(ReferencePoint.V5UTREnd, "5'UTR><L1"),
            pd(ReferencePoint.L1End, "L1>"),
            pd(ReferencePoint.L2Begin, "<L2"),
            pd(ReferencePoint.FR1Begin, "L2><FR1"),
            pd(ReferencePoint.CDR1Begin, "FR1><CDR1"),
            pd(ReferencePoint.FR2Begin, "CDR1><FR2"),
            pd(ReferencePoint.CDR2Begin, "FR2><CDR2"),
            pd(ReferencePoint.FR3Begin, "CDR2><FR3"),
            pd(ReferencePoint.CDR3Begin, "FR3><CDR3"),
            pd(ReferencePoint.VEnd, "V>", -1),
            pd(ReferencePoint.DBegin, "<D"),
            pd(ReferencePoint.DEnd, "D>", -1),
            pd(ReferencePoint.JBegin, "<J"),
            pd(ReferencePoint.CDR3End.move(-1), "CDR3><FR4").moveMarkerPoint(1),
            pd(ReferencePoint.FR4End, "FR4>", -1)
    };

    private static PointToDraw pd(ReferencePoint rp, String marker, Filter<SequencePartitioning> activator) {
        return pd(rp, marker, 0, activator);
    }

    private static PointToDraw pd(ReferencePoint rp, String marker) {
        return pd(rp, marker, 0, null);
    }

    private static PointToDraw pd(ReferencePoint rp, String marker, int additionalOffset) {
        return pd(rp, marker, additionalOffset, null);
    }

    private static PointToDraw pd(ReferencePoint rp, String marker, int additionalOffset, Filter<SequencePartitioning> activator) {
        int offset = marker.indexOf('>');
        if (offset >= 0)
            return new PointToDraw(rp.move(additionalOffset), marker, -1 - offset - additionalOffset, activator);
        offset = marker.indexOf('<');
        if (offset >= 0)
            return new PointToDraw(rp.move(additionalOffset), marker, -offset - additionalOffset, activator);
        return new PointToDraw(rp, marker, 0, activator);
    }

    public static final class PointToDraw {
        final ReferencePoint rp;
        final String marker;
        final int markerOffset;
        final Filter<SequencePartitioning> activator;

        public PointToDraw(ReferencePoint rp, String marker, int markerOffset, Filter<SequencePartitioning> activator) {
            this.rp = rp;
            this.marker = marker;
            this.markerOffset = markerOffset;
            this.activator = activator;
        }

        public PointToDraw moveMarkerPoint(int offset) {
            return new PointToDraw(rp, marker, markerOffset + offset, activator);
        }

        public boolean draw(SequencePartitioning partitioning, MultiAlignmentHelper helper, char[] line, boolean overwrite) {
            if (activator != null && !activator.accept(partitioning))
                return true;

            int positionInTarget = partitioning.getPosition(rp);
            if (positionInTarget < 0)
                return true;

            int positionInHelper = -1;
            for (int i = 0; i < helper.size(); i++)
                if (positionInTarget == helper.getAbsSubjectPositionAt(i)) {
                    positionInHelper = i;
                    break;
                }
            if (positionInHelper == -1)
                return true;

            // Checking
            if (!overwrite)
                for (int i = 0; i < marker.length(); i++) {
                    int positionInLine = positionInHelper + markerOffset + i;
                    if (positionInLine < 0 || positionInLine >= line.length)
                        continue;
                    if (line[positionInLine] != ' ')
                        return false;
                }

            for (int i = 0; i < marker.length(); i++) {
                int positionInLine = positionInHelper + markerOffset + i;
                if (positionInLine < 0 || positionInLine >= line.length)
                    continue;
                line[positionInLine] = marker.charAt(i);
            }

            return true;
        }
    }
}