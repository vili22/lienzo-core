package com.ait.lienzo.client.core.shape.wires.event;

import com.ait.lienzo.client.core.event.AbstractNodeDragEvent;
import com.ait.lienzo.client.core.event.INodeXYEvent;
import com.ait.lienzo.client.core.shape.wires.WiresShape;

/**
 * <p>Event that is fired when the drag starts ( drag produced by one of resize control points for a wires shape ).</p>
 */
public class WiresResizeStartEvent extends AbstractWiresResizeEvent<WiresResizeStartHandler> implements INodeXYEvent {

    public static final Type<WiresResizeStartHandler> TYPE = new Type<WiresResizeStartHandler>();

    public WiresResizeStartEvent( final WiresShape shape,
                                  final AbstractNodeDragEvent<?> nodeDragEvent,
                                  final int x,
                                  final int y,
                                  final double width,
                                  final double height ) {
        super( shape, nodeDragEvent, x, y, width, height );
    }

    @Override
    public Type<WiresResizeStartHandler> getAssociatedType() {
        return TYPE;
    }

    @Override
    protected void dispatch( final WiresResizeStartHandler shapeMovedHandler ) {
        shapeMovedHandler.onShapeResizeStart( this );
    }

}
