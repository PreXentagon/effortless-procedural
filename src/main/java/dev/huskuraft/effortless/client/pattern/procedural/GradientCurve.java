package dev.huskuraft.effortless.client.pattern.procedural;

public enum GradientCurve {
    LINEAR {
        @Override
        public double apply(double value, int steps) {
            return value;
        }
    },
    SMOOTHSTEP {
        @Override
        public double apply(double value, int steps) {
            return value * value * (3.0 - 2.0 * value);
        }
    },
    EASE_IN {
        @Override
        public double apply(double value, int steps) {
            return value * value;
        }
    },
    EASE_OUT {
        @Override
        public double apply(double value, int steps) {
            double inverse = 1.0 - value;
            return 1.0 - inverse * inverse;
        }
    },
    STEPPED {
        @Override
        public double apply(double value, int steps) {
            int count = Math.max(2, steps);
            return Math.round(value * (count - 1)) / (double) (count - 1);
        }
    };

    public abstract double apply(double value, int steps);
}
