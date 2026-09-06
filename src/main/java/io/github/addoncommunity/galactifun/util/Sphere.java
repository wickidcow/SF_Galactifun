package io.github.addoncommunity.galactifun.util;

import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.Validate;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.generator.LimitedRegion;

/**
 * A class for optimized generation of spheres of blocks.
 *
 * <p>Generation state is deliberately scoped to a single invocation so the same {@code Sphere}
 * can safely be reused by Paper's parallel chunk-generation workers.</p>
 *
 * @author Mooy1
 */
public final class Sphere {

    public static final int MIN_RADIUS = 3;
    public static final int MAX_RADIUS = 125;

    private final Material[] materials;

    public Sphere(@Nonnull Material... materials) {
        Validate.isTrue(materials.length != 0);
        this.materials = materials.clone();
    }

    public void generate(@Nonnull Location middle, @Nonnull LimitedRegion region, int min, int dev) {
        Validate.isTrue(min >= MIN_RADIUS && dev >= 0 && min + dev <= MAX_RADIUS,
                "Generation parameters out of bounds!");

        GenerationState state = new GenerationState(middle, region);

        // radius
        int radius = min + ThreadLocalRandom.current().nextInt(dev + 1);
        int radiusSquared = radius * radius;

        // center block
        state.gen(0, 0, 0);

        // outer middle blocks, furthest from middle
        state.genMiddles(radius);

        for (int x = 1, vector1 = 1; x < radius; vector1 += (x++ << 1) + 1) {

            // middle blocks
            state.genMiddles(x);

            for (int y = x, vector2 = vector1 + y * y; y < radius; vector2 += (y++ << 1) + 1) {

                // check radius
                if (vector2 < radiusSquared) {

                    // edges
                    state.genEdges(x, y);
                    if (x != y) {
                        state.genEdges(y, x);
                    }

                } else {
                    break;
                }

                for (int z = y, vector3 = vector2 + z * z; z < radius; vector3 += (z++ << 1) + 1) {

                    // check within radius
                    if (vector3 < radiusSquared) {

                        // corners
                        state.genCorners(x, y, z);
                        if (x != y) {
                            state.genCorners(y, x, z);
                            state.genCorners(z, y, x);
                            if (y != z) {
                                state.genCorners(x, z, y);
                                state.genCorners(z, x, y);
                                state.genCorners(y, z, x);
                            }
                        } else if (x != z) {
                            state.genCorners(z, y, x);
                            state.genCorners(x, z, y);
                        }

                    } else {
                        break;
                    }
                }
            }
        }
    }

    /**
     * Mutable cursor for one sphere generation only. Keeping it local prevents two chunk workers from
     * sharing a material index, middle location, or LimitedRegion while populators run concurrently.
     */
    private final class GenerationState {

        private final Location middle;
        private final LimitedRegion region;
        private int currentMaterial;

        private GenerationState(Location middle, LimitedRegion region) {
            this.middle = middle;
            this.region = region;
        }

        private void genMiddles(int a) {
            gen(a, 0, 0);
            gen(-a, 0, 0);
            gen(0, a, 0);
            randomize();
            gen(0, -a, 0);
            gen(0, 0, a);
            gen(0, 0, -a);
        }

        private void genEdges(int a, int b) {
            gen(a, b, 0);
            gen(-a, b, 0);
            randomize();
            gen(a, -b, 0);
            gen(-a, -b, 0);
            gen(0, a, b);
            gen(0, -a, b);
            randomize();
            gen(0, a, -b);
            gen(0, -a, -b);
            gen(a, 0, b);
            gen(-a, 0, b);
            randomize();
            gen(a, 0, -b);
            gen(-a, 0, -b);
        }

        private void genCorners(int a, int b, int c) {
            gen(a, b, c);
            gen(-a, b, c);
            gen(a, -b, c);
            gen(a, b, -c);
            randomize();
            gen(-a, -b, c);
            gen(a, -b, -c);
            gen(-a, b, -c);
            gen(-a, -b, -c);
        }

        private void gen(int x, int y, int z) {
            region.setType(middle.clone().add(x, y, z), materials[currentMaterial++]);
            if (currentMaterial == materials.length) {
                currentMaterial = 0;
            }
        }

        private void randomize() {
            currentMaterial = ThreadLocalRandom.current().nextInt(materials.length);
        }
    }
}
