package com.screeneo.u5irremote;

import java.util.Arrays;

final class IrSignal {
    private static final int NEC_FREQUENCY = 38000;
    private static final int NEC_PULSE = 563;
    static final int REPEAT_INTERVAL_MS = 108;
    private static final int NEC_PERIOD = REPEAT_INTERVAL_MS * 1000;
    private static final int MAX_DURATION = 2000000;
    private static final int[] NEC_REPEAT = {9000, 2250, NEC_PULSE,
            NEC_PERIOD - 9000 - 2250 - NEC_PULSE};

    final int frequency = NEC_FREQUENCY;
    final int[] pattern;

    private IrSignal(int[] pattern) {
        this.pattern = pattern;
    }

    static IrSignal changhong(int command) {
        int[] bytes = {0x40, 0xbf, command, command ^ 0xff};
        int[] pattern = new int[68];
        int index = 0;
        pattern[index++] = 9000;
        pattern[index++] = 4500;
        for (int value : bytes) {
            for (int bit = 0; bit < 8; bit++) {
                pattern[index++] = NEC_PULSE;
                pattern[index++] = (value & (1 << bit)) == 0 ? NEC_PULSE : 1688;
            }
        }
        pattern[index] = NEC_PULSE;
        int duration = 0;
        for (int pulse : pattern) duration += pulse;
        pattern[index + 1] = NEC_PERIOD - duration;
        return new IrSignal(pattern);
    }

    int[] heldPattern() {
        int repeats = (MAX_DURATION - 1) / NEC_PERIOD - 1;
        int[] held = Arrays.copyOf(pattern, pattern.length + repeats * NEC_REPEAT.length);
        for (int offset = pattern.length; offset < held.length; offset += NEC_REPEAT.length) {
            System.arraycopy(NEC_REPEAT, 0, held, offset, NEC_REPEAT.length);
        }
        return held;
    }

    static int[] repeatPattern() {
        return NEC_REPEAT;
    }
}
