package com.oidioomstudio.fastminer.shape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ShapeRegistry {
    private static final List<FastMinerShape> SHAPES = new ArrayList<>();

    static {
        // Shapes in identical order as FTB Ultimine
        SHAPES.add(new ShapelessShape());
        SHAPES.add(new SmallTunnelShape());
        SHAPES.add(new LargeTunnelShape());
        SHAPES.add(new MiningTunnelShape());
        SHAPES.add(new EscapeTunnelShape());
    }

    public static List<FastMinerShape> getShapes() {
        return Collections.unmodifiableList(SHAPES);
    }

    public static int getCount() {
        return SHAPES.size();
    }

    public static FastMinerShape getShape(int index) {
        if (SHAPES.isEmpty()) return null;
        int size = SHAPES.size();
        int wrapped = (index % size + size) % size;
        return SHAPES.get(wrapped);
    }
}
