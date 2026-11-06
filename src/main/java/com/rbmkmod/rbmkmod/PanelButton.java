package com.rbmkmod.rbmkmod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Przyciski / suwaki na panelu sterowania. Współrzędne (w pikselach modelu 0-16) są
 * skopiowane 1:1 z elementów modelu JSON, razem z ich rotacją wokół osi X.
 */
public enum PanelButton {
    //             from (x,y,z)        to (x,y,z)          kąt    origin rotacji   suwak?
    RODS_BORON      (3,    3.2,  5,    7,    4.2,  6,      -22.5, 3,  2.2,  4,  false),
    RODS_GRAPHITE   (3,    2.35, 3,    7,    3.35, 4,      -22.5, 3,  1.35, 2,  false),
    AZ5             (9,    4,    6,    12,   5,    8,      -22.5, 9,  3,    6,  false),
    AUTO_AZ5_TOGGLE (13,   4,    7,    15,   5,    8,      -22.5, 11, 3,    6,  false),
    AUTO_AZ5_SLIDER (10.5, 7,    7.8,  15.5, 8,    8.8,    0,     0,  0,    0,  true),
    GRAPHITE_SLIDER (10.5, 9,    7.8,  15.5, 10,   8.8,    0,     0,  0,    0,  true),
    Y_MINUS         (13.5, 11,   7.8,  15.5, 12,   8.8,    0,     0,  0,    0,  false),
    Y_PLUS          (10.5, 11,   7.8,  12.5, 12,   8.8,    0,     0,  0,    0,  false);

    /** Jeśli kliknięcia w przyciski na pochyłym pulpicie trafiają "obok", zmień na -1. */
    private static final double ROT_SIGN = 1.0;
    /** Tolerancja trafienia w pikselach modelu (przyciski mają tylko 1 px grubości). */
    private static final double EPS = 0.3;

    private static final PanelButton[] VALUES = values();

    private final double minX, minY, minZ, maxX, maxY, maxZ;
    private final double angle, ox, oy, oz;
    public final boolean slider;

    PanelButton(double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
                double angle, double ox, double oy, double oz, boolean slider) {
        this.minX = minX; this.minY = minY; this.minZ = minZ;
        this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        this.angle = angle; this.ox = ox; this.oy = oy; this.oz = oz;
        this.slider = slider;
    }

    public static PanelButton byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : null;
    }

    /** Wartość suwaka dla pozycji 0..1 wzdłuż jego tła. */
    public int valueFor(double frac) {
        return switch (this) {
            case AUTO_AZ5_SLIDER -> 300 + (int) Math.round(frac * 42) * 50; // 300..2400 co 50 °C
            case GRAPHITE_SLIDER -> (int) Math.round(frac * 20) * 5;        // 0..100 co 5 %
            default -> 0;
        };
    }

    public record Hit(PanelButton button, double t, double frac) {}

    /**
     * Sprawdza, w który przycisk panelu trafia promień eye -> end.
     * @return najbliższe trafienie lub null
     */
    public static Hit raycast(BlockPos pos, Direction facing, Vec3 eye, Vec3 end) {
        Vec3 a = toModel(eye, pos, facing);
        Vec3 b = toModel(end, pos, facing);
        Hit best = null;

        for (PanelButton btn : VALUES) {
            Vec3 pa = a, pb = b;
            if (btn.angle != 0) {
                Vec3 origin = new Vec3(btn.ox, btn.oy, btn.oz);
                pa = unrotateX(a, btn.angle, origin);
                pb = unrotateX(b, btn.angle, origin);
            }
            double[] o  = {pa.x, pa.y, pa.z};
            double[] d  = {pb.x - pa.x, pb.y - pa.y, pb.z - pa.z};
            double[] lo = {btn.minX - EPS, btn.minY - EPS, btn.minZ - EPS};
            double[] hi = {btn.maxX + EPS, btn.maxY + EPS, btn.maxZ + EPS};

            double tMin = 0, tMax = 1;
            boolean ok = true;
            for (int i = 0; i < 3 && ok; i++) {
                if (Math.abs(d[i]) < 1e-9) {
                    if (o[i] < lo[i] || o[i] > hi[i]) ok = false;
                } else {
                    double t1 = (lo[i] - o[i]) / d[i];
                    double t2 = (hi[i] - o[i]) / d[i];
                    if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
                    tMin = Math.max(tMin, t1);
                    tMax = Math.min(tMax, t2);
                    if (tMin > tMax) ok = false;
                }
            }
            if (!ok) continue;

            if (best == null || tMin < best.t()) {
                double x = o[0] + d[0] * tMin;
                double frac = Math.max(0, Math.min(1, (x - btn.minX) / (btn.maxX - btn.minX)));
                best = new Hit(btn, tMin, frac);
            }
        }
        return best;
    }

    /** Świat -> przestrzeń modelu (piksele 0-16), odwrotność transformacji z renderera. */
    private static Vec3 toModel(Vec3 world, BlockPos pos, Direction facing) {
        double rx = world.x - pos.getX() - 0.5;
        double ry = world.y - pos.getY();
        double rz = world.z - pos.getZ() - 0.5;

        double deg = switch (facing) {
            case SOUTH -> 180;
            case WEST -> 90;
            case EAST -> 270;
            default -> 0;
        };
        double r = Math.toRadians(deg);
        double c = Math.cos(r), s = Math.sin(r);
        // odwrotność obrotu Axis.YP.rotationDegrees(deg)
        double ux = rx * c - rz * s;
        double uz = rx * s + rz * c;

        // odwrotność skalowania 2x2x1 + przeliczenie na piksele
        return new Vec3((ux + 0.5) * 8.0, ry * 8.0, (uz + 0.5) * 16.0);
    }

    /** Odwrotność rotacji elementu modelu wokół osi X. */
    private static Vec3 unrotateX(Vec3 p, double modelAngleDeg, Vec3 origin) {
        double a = Math.toRadians(-modelAngleDeg * ROT_SIGN);
        double ry = p.y - origin.y;
        double rz = p.z - origin.z;
        double cos = Math.cos(a), sin = Math.sin(a);
        return new Vec3(p.x,
                origin.y + ry * cos - rz * sin,
                origin.z + ry * sin + rz * cos);
    }
}
