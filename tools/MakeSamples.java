import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

/** Génère 8 images d'illustration libres de droits (dessinées par code) dans ./samples. */
public class MakeSamples {
    static final int W = 960, H = 640;
    static long SEED = 0;

    public static void main(String[] a) throws Exception {
        File dir = new File(a.length > 0 ? a[0] : "samples");
        dir.mkdirs();
        String[] names = {"coucher-de-soleil", "montagnes", "foret", "ville-nuit", "plage", "aurore", "desert", "geometrie"};
        int variants = a.length > 1 ? Integer.parseInt(a[1]) : 5;
        // SEED 0 : les 8 images d'origine ; SEED 1..n : variantes (autres reliefs, étoiles, nuages… + léger jitter de couleur)
        for (SEED = 0; SEED <= variants; SEED++) {
            BufferedImage[] images = {sunset(), mountains(), forest(), city(), beach(), aurora(), desert(), geometry()};
            for (int i = 0; i < images.length; i++) {
                BufferedImage img = SEED == 0 ? images[i] : jitter(images[i], new Random(SEED * 31 + i));
                save(dir, names[i] + (SEED == 0 ? "" : "-" + (SEED + 1)) + ".png", img);
            }
        }
    }

    /** Variante d'une image : miroir aléatoire, luminosité et gains de couleur légèrement différents. */
    static BufferedImage jitter(BufferedImage src, Random r) {
        BufferedImage out = canvas();
        Graphics2D g = out.createGraphics();
        if (r.nextBoolean()) g.drawImage(src, W, 0, -W, H, null); else g.drawImage(src, 0, 0, null);
        g.dispose();
        float[] scales = {0.88f + r.nextFloat() * 0.24f, 0.88f + r.nextFloat() * 0.24f, 0.88f + r.nextFloat() * 0.24f, 1f};
        float[] offsets = {r.nextInt(17) - 8, r.nextInt(17) - 8, r.nextInt(17) - 8, 0};
        return new java.awt.image.RescaleOp(scales, offsets, null).filter(out, null);
    }

    static void save(File dir, String name, BufferedImage img) throws Exception {
        ImageIO.write(img, "png", new File(dir, name));
    }

    static BufferedImage canvas() { return new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB); }

    static Graphics2D g(BufferedImage i) {
        Graphics2D g = i.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        return g;
    }

    static void sky(Graphics2D g, Color top, Color bottom, int h) {
        g.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
        g.fillRect(0, 0, W, H);
    }

    static void sun(Graphics2D g, int x, int y, int r, Color c) {
        for (int i = 6; i >= 1; i--) {
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 18));
            int rr = r + i * 22;
            g.fillOval(x - rr, y - rr, rr * 2, rr * 2);
        }
        g.setColor(c);
        g.fillOval(x - r, y - r, r * 2, r * 2);
    }

    static Path2D ridge(Random rnd, int base, int amp, double rough) {
        Path2D p = new Path2D.Double();
        p.moveTo(0, H);
        double y = base;
        for (int x = 0; x <= W; x += 16) {
            y += (rnd.nextDouble() - 0.5) * rough;
            y = Math.max(base - amp, Math.min(base + amp, y));
            p.lineTo(x, y);
        }
        p.lineTo(W, H);
        p.closePath();
        return p;
    }

    static BufferedImage sunset() {
        BufferedImage i = canvas(); Graphics2D g = g(i);
        sky(g, new Color(0x2b1055), new Color(0xff8a4c), 430);
        sun(g, 640, 400, 70, new Color(0xffd27a));
        g.setColor(new Color(0x1a0b33)); g.fill(ridge(new Random(3 + SEED * 1000), 470, 40, 36));
        g.setColor(new Color(0x0e0620)); g.fill(ridge(new Random(8 + SEED * 1000), 560, 30, 30));
        return i;
    }

    static BufferedImage mountains() {
        BufferedImage i = canvas(); Graphics2D g = g(i);
        sky(g, new Color(0x6fb1e8), new Color(0xe8f4ff), 400);
        sun(g, 170, 130, 38, new Color(0xfff2b0));
        Color[] c = { new Color(0xa9bcd6), new Color(0x7f98bb), new Color(0x4f6a94), new Color(0x2f4468) };
        for (int k = 0; k < c.length; k++) {
            g.setColor(c[k]);
            Path2D p = ridge(new Random(20 + k), 330 + k * 75, 90 - k * 12, 70 - k * 10);
            g.fill(p);
        }
        g.setColor(Color.WHITE);
        g.fillPolygon(new int[]{420, 470, 520, 495, 470, 445}, new int[]{200, 120, 200, 215, 190, 215}, 6);
        return i;
    }

    static BufferedImage forest() {
        BufferedImage i = canvas(); Graphics2D g = g(i);
        sky(g, new Color(0xcfe9d6), new Color(0x7fb38e), 500);
        Random r = new Random(5 + SEED * 1000);
        for (int layer = 0; layer < 4; layer++) {
            int shade = 0x2d6a4f - layer * 0x0a1208;
            g.setColor(new Color(shade));
            for (int n = 0; n < 16 + layer * 5; n++) {
                int x = r.nextInt(W), base = 380 + layer * 70 + r.nextInt(40), h = 150 + r.nextInt(120) + layer * 20, w = 50 + r.nextInt(40);
                g.fillPolygon(new int[]{x - w, x, x + w}, new int[]{base, base - h, base}, 3);
                g.fillPolygon(new int[]{x - w * 3 / 4, x, x + w * 3 / 4}, new int[]{base - h / 3, base - h - 20, base - h / 3}, 3);
            }
        }
        g.setColor(new Color(0x143d2c)); g.fillRect(0, 560, W, 80);
        return i;
    }

    static BufferedImage city() {
        BufferedImage i = canvas(); Graphics2D g = g(i);
        sky(g, new Color(0x050816), new Color(0x2a2f6b), 520);
        Random r = new Random(11 + SEED * 1000);
        g.setColor(Color.WHITE);
        for (int n = 0; n < 90; n++) g.fillRect(r.nextInt(W), r.nextInt(300), 2, 2);
        int x = 0;
        while (x < W) {
            int w = 50 + r.nextInt(70), h = 140 + r.nextInt(300);
            g.setColor(new Color(0x0b1030 + r.nextInt(0x101010)));
            g.fillRect(x, H - h, w, h);
            for (int wy = H - h + 14; wy < H - 14; wy += 22)
                for (int wx = x + 8; wx < x + w - 10; wx += 16)
                    if (r.nextInt(3) > 0) { g.setColor(r.nextBoolean() ? new Color(0xffd86b) : new Color(0x7fe3ff)); g.fillRect(wx, wy, 7, 11); }
            x += w + 4;
        }
        return i;
    }

    static BufferedImage beach() {
        BufferedImage i = canvas(); Graphics2D g = g(i);
        sky(g, new Color(0x4fb3ff), new Color(0xcdeeff), 300);
        sun(g, 780, 110, 44, new Color(0xfff3a8));
        g.setPaint(new GradientPaint(0, 300, new Color(0x1aa0c8), 0, 440, new Color(0x7fe0e6)));
        g.fillRect(0, 300, W, 150);
        g.setColor(new Color(255, 255, 255, 170));
        for (int k = 0; k < 7; k++) g.draw(new Arc2D.Double(-40 + k * 150, 395 + (k % 2) * 8, 200, 24, 0, 180, Arc2D.OPEN));
        g.setPaint(new GradientPaint(0, 440, new Color(0xf4dba5), 0, H, new Color(0xe0b878)));
        g.fill(ridge(new Random(2 + SEED * 1000), 450, 8, 6));
        g.setColor(new Color(0x7b4a2a)); g.fillRect(150, 300, 10, 190);
        g.setColor(new Color(0x2f8f4e));
        for (int k = 0; k < 5; k++) { AffineTransform t = g.getTransform(); g.rotate(Math.toRadians(-70 + k * 35), 155, 300); g.fillOval(155, 292, 120, 16); g.setTransform(t); }
        return i;
    }

    static BufferedImage aurora() {
        BufferedImage i = canvas(); Graphics2D g = g(i);
        sky(g, new Color(0x020611), new Color(0x0b2a3a), H);
        for (int k = 0; k < 5; k++) {
            Path2D p = new Path2D.Double(); p.moveTo(0, 360);
            for (int x = 0; x <= W; x += 20) p.lineTo(x, 200 + 70 * Math.sin(x / 130.0 + k) + k * 22);
            p.lineTo(W, 520); p.lineTo(0, 520); p.closePath();
            g.setPaint(new GradientPaint(0, 150 + k * 20, new Color(60, 255, 170, 110), 0, 480, new Color(110, 90, 255, 0)));
            g.fill(p);
        }
        Random r = new Random(7 + SEED * 1000); g.setColor(Color.WHITE);
        for (int n = 0; n < 120; n++) g.fillRect(r.nextInt(W), r.nextInt(380), 2, 2);
        g.setColor(new Color(0x04080f)); g.fill(ridge(new Random(9 + SEED * 1000), 540, 30, 28));
        return i;
    }

    static BufferedImage desert() {
        BufferedImage i = canvas(); Graphics2D g = g(i);
        sky(g, new Color(0xffb36b), new Color(0xffe9b8), 380);
        sun(g, 480, 330, 52, new Color(0xfff6d1));
        Color[] c = { new Color(0xe9a15a), new Color(0xd3843f), new Color(0xb2662e), new Color(0x8a4a23) };
        for (int k = 0; k < c.length; k++) {
            g.setColor(c[k]);
            Path2D p = new Path2D.Double(); p.moveTo(0, H);
            for (int x = 0; x <= W; x += 12) p.lineTo(x, 400 + k * 62 + 36 * Math.sin(x / (150.0 - k * 18) + k * 2));
            p.lineTo(W, H); p.closePath(); g.fill(p);
        }
        return i;
    }

    static BufferedImage geometry() {
        BufferedImage i = canvas(); Graphics2D g = g(i);
        sky(g, new Color(0x1d2b64), new Color(0xf8cdda), H);
        Color[] c = { new Color(0xff5d73), new Color(0xffd166), new Color(0x06d6a0), new Color(0x4cc9f0), new Color(0xb980f0) };
        for (int k = 0; k < 5; k++) {
            g.setColor(new Color(c[k].getRed(), c[k].getGreen(), c[k].getBlue(), 200));
            g.fillOval(100 + k * 150, 120 + (k % 2) * 160, 220, 220);
        }
        g.setColor(new Color(255, 255, 255, 230)); g.setStroke(new BasicStroke(4));
        g.drawRect(60, 60, W - 120, H - 120);
        g.fillPolygon(new int[]{480, 560, 400}, new int[]{440, 590, 590}, 3);
        return i;
    }
}
