package org.klomp.cassowary.layout;

import java.awt.Component;

public enum Attribute {
    LEFT, RIGHT, TOP, BOTTOM, WIDTH, HEIGHT; // , CENTERX, CENTERY, BASELINE, LEADING, TRAILING;

    public boolean isSize() {
        return this == WIDTH || this == HEIGHT;
    }

    public int lookup(Component c) {
        Attribute attr = this;
        // if (c.getParent() == null || c.getParent().getComponentOrientation().isLeftToRight()) {
        // switch (attr) {
        // case LEADING:
        // attr = LEFT;
        // case TRAILING:
        // attr = RIGHT;
        // }
        // } else {
        // switch (attr) {
        // case LEADING:
        // attr = RIGHT;
        // case TRAILING:
        // attr = LEFT;
        // }
        // }

        switch (attr) {
        case LEFT:
            return c.getX();
        case RIGHT:
            return c.getX() + c.getWidth();
        case TOP:
            return c.getY();
        case BOTTOM:
            return c.getY() + c.getHeight();
        case WIDTH:
            return c.getWidth();
        case HEIGHT:
            return c.getHeight();
            // case CENTERX:
            // return c.getX() + c.getWidth() / 2;
            // case CENTERY:
            // return c.getY() + c.getHeight() / 2;
            // case BASELINE:
            // int baseline = c.getBaseline(c.getWidth(), c.getHeight());
            // if (baseline < 0) {
            // // Component doesn't have a baseline, use the bottom.
            // return c.getY() + c.getHeight();
            // }
            // return c.getY() + baseline;
        default:
            throw new Error();
        }
    }

    /**
     * Because of the way in which BASELINE, RIGHT, BOTTOM, LEADING, TRAILING, CENTERX and CENTERY are calculated, it is important
     * to first apply WIDTH and HEIGHT values.
     * 
     * @param c
     * @param value
     */
    public void apply(Component c, int value) {
        Attribute attr = this;
        // if (c.getParent() == null || c.getParent().getComponentOrientation().isLeftToRight()) {
        // switch (attr) {
        // case LEADING:
        // attr = LEFT;
        // case TRAILING:
        // attr = RIGHT;
        // }
        // } else {
        // switch (attr) {
        // case LEADING:
        // attr = RIGHT;
        // case TRAILING:
        // attr = LEFT;
        // }
        // }

        switch (attr) {
        case LEFT:
            c.setLocation(value, c.getY());
            break;
        case RIGHT:
            c.setLocation(value - c.getWidth(), c.getY());
            break;
        case TOP:
            c.setLocation(c.getX(), value);
            break;
        case BOTTOM:
            c.setLocation(c.getX(), value - c.getHeight());
            break;
        case WIDTH:
            c.setSize(value, c.getHeight());
            break;
        case HEIGHT:
            c.setSize(c.getWidth(), value);
            break;
        // case CENTERX:
        // c.setLocation(value - c.getWidth() / 2, c.getY());
        // break;
        // case CENTERY:
        // c.setLocation(c.getX(), value - c.getHeight() / 2);
        // case BASELINE:
        // int baseline = c.getBaseline(c.getWidth(), c.getHeight());
        // if (baseline < 0) {
        // // Component doesn't have a baseline, use the bottom.
        // c.setLocation(c.getX(), value - c.getHeight());
        // } else {
        // c.setLocation(c.getX(), value - baseline);
        // }
        // break;
        default:
            throw new Error();
        }
    }
}
