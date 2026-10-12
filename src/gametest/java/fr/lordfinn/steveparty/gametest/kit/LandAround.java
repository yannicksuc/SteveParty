package fr.lordfinn.steveparty.gametest.kit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * How far beyond its template this one test plays, in blocks, in place of its class's {@link SteveGameTest#landAround()}:
 * the test that goes far asks for the land it needs, its neighbours keep loading little (a wide land, loaded for every
 * test of a class, made them wait for it in vain on a busy machine).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LandAround {
    int value();
}
