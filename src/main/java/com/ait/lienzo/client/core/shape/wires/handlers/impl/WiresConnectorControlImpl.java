package com.ait.lienzo.client.core.shape.wires.handlers.impl;

import com.ait.lienzo.client.core.Context2D;
import com.ait.lienzo.client.core.event.*;
import com.ait.lienzo.client.core.shape.AbstractDirectionalMultiPointShape;
import com.ait.lienzo.client.core.shape.IPrimitive;
import com.ait.lienzo.client.core.shape.Node;
import com.ait.lienzo.client.core.shape.Shape;
import com.ait.lienzo.client.core.shape.wires.*;
import com.ait.lienzo.client.core.shape.wires.handlers.WiresConnectionControl;
import com.ait.lienzo.client.core.shape.wires.handlers.WiresConnectorControl;
import com.ait.lienzo.client.core.shape.wires.handlers.WiresDragControlContext;
import com.ait.lienzo.client.core.types.*;
import com.ait.lienzo.client.core.util.ScratchPad;
import com.ait.lienzo.client.widget.DragConstraintEnforcer;
import com.ait.lienzo.client.widget.DragContext;
import com.ait.tooling.nativetools.client.collection.NFastDoubleArray;
import com.ait.tooling.nativetools.client.collection.NFastDoubleArrayJSO;
import com.ait.tooling.nativetools.client.collection.NFastStringMap;
import com.ait.tooling.nativetools.client.event.HandlerRegistrationManager;
import com.google.gwt.user.client.Timer;

public class WiresConnectorControlImpl implements WiresConnectorControl {

    private WiresConnector m_connector;

    private HandlerRegistrationManager m_HandlerRegistrationManager;

    private Timer m_timer;

    private WiresManager m_wiresManager;

    private NFastDoubleArray m_startPoints;

    public WiresConnectorControlImpl( final WiresConnector connector,
                                      final WiresManager wiresManager ) {
        m_connector = connector;
        m_wiresManager = wiresManager;
    }

    /*
        ***************** DRAG **********************************
     */


    @Override
    public void dragStart( final Context context ) {

        IControlHandleList handles = m_connector.getPointHandles();

        m_startPoints = new NFastDoubleArray();

        for ( int i = 0; i < handles.size(); i++ ) {
            IControlHandle h = handles.getHandle( i );
            IPrimitive<?> prim = h.getControl();
            m_startPoints.push( prim.getX() );
            m_startPoints.push( prim.getY() );
        }


    }

    @Override
    public void dragMove( final Context context ) {

        IControlHandleList handles = m_connector.getPointHandles();

        for ( int i = 0, j = 0; i < handles.size(); i++, j += 2 ) {
            IControlHandle h = handles.getHandle( i );
            IPrimitive<?> prim = h.getControl();
            prim.setX( m_startPoints.get( j ) + context.getX() );
            prim.setY( m_startPoints.get( j + 1 ) + context.getY() );
        }

        m_wiresManager.getLayer().getLayer().batch();

    }

    @Override
    public void dragEnd( final Context context ) {

        m_connector.getGroup().setX( 0 ).setY( 0 );

        Point2DArray points = m_connector.getLine().getPoint2DArray();
        IControlHandleList handles = m_connector.getPointHandles();

        for ( int i = 0, j = 0; i < handles.size(); i++, j += 2 ) {
            Point2D p = points.get( i );
            p.setX( p.getX() + context.getX() );
            p.setY( p.getY() + context.getY() );

            IControlHandle h = handles.getHandle( i );
            IPrimitive<?> prim = h.getControl();
            prim.setX( m_startPoints.get( j ) + context.getX() );
            prim.setY( m_startPoints.get( j + 1 ) + context.getY() );
        }

        m_connector.getLine().refresh();

        m_wiresManager.getLayer().getLayer().batch();

        m_startPoints = null;

    }

    @Override
    public boolean dragAdjust( final Point2D dxy ) {
        return false;
    }

    /*
        ***************** CONTROL POINTS **********************************
     */

    @Override
    public void addControlPoint( final double x, final double y ) {

        m_connector.destroyPointHandles();
        Point2DArray oldPoints = m_connector.getLine().getPoint2DArray();

        int pointIndex = getIndexForSelectedSegment( (int) x, (int) y, oldPoints );
        if ( pointIndex > 0 ) {
            Point2D point = new Point2D( x, y );
            Point2DArray newPoints = new Point2DArray();
            newPoints.push( oldPoints.get( 0 ) );
            for ( int i = 1; i < pointIndex; i++ ) {
                newPoints.push( oldPoints.get( i ) );
            }
            newPoints.push( point );
            for ( int i = pointIndex; i < oldPoints.size(); i++ ) {
                newPoints.push( oldPoints.get( i ) );
            }
            m_connector.getLine().setPoint2DArray( newPoints );
        }

        showPointHandles();

    }

    @Override
    public void destroyControlPoint( final Object control ) {

        IControlHandle selected = null;

        for ( IControlHandle handle : m_connector.getPointHandles() ) {
            if ( handle.getControl() == control ) {
                selected = handle;

                break;
            }
        }
        if ( null == selected ) {
            return;
        }
        Point2DArray oldPoints = m_connector.getLine().getPoint2DArray();

        Point2DArray newPoints = new Point2DArray();

        Point2D selectedPoint2D = selected.getControl().getLocation();

        for ( int i = 0; i < oldPoints.size(); i++ ) {
            Point2D current = oldPoints.get( i );

            if ( !current.equals( selectedPoint2D ) ) {
                newPoints.push( current );
            }
        }
        m_connector.destroyPointHandles();

        m_connector.getLine().setPoint2DArray( newPoints );

        showPointHandles();

    }

    @Override
    public void showControlPoints() {

        if ( this.m_HandlerRegistrationManager == null ) {
            cancelTimer();
            showPointHandles();
        }

    }

    @Override
    public void hideControlPoints() {

        if ( m_HandlerRegistrationManager != null ) {
            createHideTimer();
        }

    }

    private int getIndexForSelectedSegment( final int mouseX,
                                            final int mouseY,
                                            final Point2DArray oldPoints ) {
        NFastStringMap<Integer> colorMap = new NFastStringMap<Integer>();

        AbstractDirectionalMultiPointShape<?> line = m_connector.getLine();
        ScratchPad scratch = line.getScratchPad();
        scratch.clear();
        PathPartList path = line.getPathPartList();
        int pointsIndex = 1;
        String color = MagnetManager.m_c_rotor.next();
        colorMap.put( color, pointsIndex );
        Context2D ctx = scratch.getContext();
        double strokeWidth = line.getStrokeWidth();
        ctx.setStrokeWidth( strokeWidth );

        Point2D absolutePos = WiresUtils.getLocation( m_connector.getLine() );
        double offsetX = absolutePos.getX();
        double offsetY = absolutePos.getY();

        Point2D pathStart = new Point2D( offsetX, offsetY );
        Point2D segmentStart = pathStart;

        for ( int i = 0; i < path.size(); i++ ) {
            PathPartEntryJSO entry = path.get( i );
            NFastDoubleArrayJSO points = entry.getPoints();

            switch ( entry.getCommand() ) {
                case PathPartEntryJSO.MOVETO_ABSOLUTE: {
                    double x0 = points.get( 0 ) + offsetX;
                    double y0 = points.get( 1 ) + offsetY;
                    Point2D m = new Point2D( x0, y0 );
                    if ( i == 0 ) {
                        // this is position is needed, if we close the path.
                        pathStart = m;
                    }
                    segmentStart = m;
                    break;
                }
                case PathPartEntryJSO.LINETO_ABSOLUTE: {
                    points = entry.getPoints();
                    double x0 = points.get( 0 ) + offsetX;
                    double y0 = points.get( 1 ) + offsetY;
                    Point2D end = new Point2D( x0, y0 );

                    if ( oldPoints.get( pointsIndex ).equals( segmentStart ) ) {
                        pointsIndex++;
                        color = MagnetManager.m_c_rotor.next();
                        colorMap.put( color, pointsIndex );
                    }
                    ctx.setStrokeColor( color );

                    ctx.beginPath();
                    ctx.moveTo( segmentStart.getX(), segmentStart.getY() );
                    ctx.lineTo( x0, y0 );
                    ctx.stroke();
                    segmentStart = end;
                    break;
                }
                case PathPartEntryJSO.CLOSE_PATH_PART: {
                    double x0 = pathStart.getX() + offsetX;
                    double y0 = pathStart.getY() + offsetY;
                    Point2D end = new Point2D( x0, y0 );
                    if ( oldPoints.get( pointsIndex ).equals( segmentStart ) ) {
                        pointsIndex++;
                        color = MagnetManager.m_c_rotor.next();
                        colorMap.put( color, pointsIndex );
                    }
                    ctx.setStrokeColor( color );
                    ctx.beginPath();
                    ctx.moveTo( segmentStart.getX(), segmentStart.getY() );
                    ctx.lineTo( x0, y0 );
                    ctx.stroke();
                    segmentStart = end;
                    break;
                }
                case PathPartEntryJSO.CANVAS_ARCTO_ABSOLUTE: {
                    points = entry.getPoints();

                    double x0 = points.get( 0 ) + offsetX;
                    double y0 = points.get( 1 ) + offsetY;
                    Point2D p0 = new Point2D( x0, y0 );

                    double x1 = points.get( 2 ) + offsetX;
                    double y1 = points.get( 3 ) + offsetY;
                    double r = points.get( 4 );
                    Point2D p1 = new Point2D( x1, y1 );
                    Point2D end = p1;

                    if ( p0.equals( oldPoints.get( pointsIndex ) ) ) {
                        pointsIndex++;
                        color = MagnetManager.m_c_rotor.next();
                        colorMap.put( color, pointsIndex );
                    }
                    ctx.setStrokeColor( color );
                    ctx.beginPath();
                    ctx.moveTo( segmentStart.getX(), segmentStart.getY() );
                    ctx.arcTo( x0, y0, x1, y1, r );
                    ctx.stroke();

                    segmentStart = end;
                    break;
                }

            }
        }

        BoundingBox box = m_connector.getLine().getBoundingBox();

        // Keep the ImageData small by clipping just the visible line area
        // But remember the mouse must be offset for this clipped area.
        int sx = ( int ) ( box.getX() - strokeWidth - offsetX );
        int sy = ( int ) ( box.getY() - strokeWidth - offsetY );

        ImageData backing = ctx.getImageData( sx, sy, ( int ) ( box.getWidth() + strokeWidth + strokeWidth ), ( int ) ( box.getHeight() + strokeWidth + strokeWidth ) );

        color = BackingColorMapUtils.findColorAtPoint( backing, mouseX - sx, mouseY - sy );
        pointsIndex = colorMap.get( color );
        return pointsIndex;
    }

    private void showPointHandles() {
        if ( m_HandlerRegistrationManager == null ) {
            m_HandlerRegistrationManager = m_connector.getPointHandles().getHandlerRegistrationManager();
        }
        m_connector.getPointHandles().show();

        final ConnectionHandler connectionHandler = new ConnectionHandler();

        Shape<?> head = m_connector.getHeadConnection().getControl().asShape();
        head.setDragConstraints( connectionHandler );
        m_HandlerRegistrationManager.register( head.addNodeDragEndHandler( connectionHandler ) );

        Shape<?> tail = m_connector.getTailConnection().getControl().asShape();
        tail.setDragConstraints( connectionHandler );
        m_HandlerRegistrationManager.register( tail.addNodeDragEndHandler( connectionHandler ) );

        final WiresConnectorControlHandler controlPointsHandler = new WiresConnectorControlHandler();

        for ( IControlHandle handle : m_connector.getPointHandles() ) {
            Shape<?> shape = handle.getControl().asShape();
            m_HandlerRegistrationManager.register( shape.addNodeMouseEnterHandler( controlPointsHandler ) );
            m_HandlerRegistrationManager.register( shape.addNodeMouseExitHandler( controlPointsHandler ) );
            m_HandlerRegistrationManager.register( shape.addNodeMouseDoubleClickHandler( controlPointsHandler ) );
        }

    }

    private final class ConnectionHandler implements DragConstraintEnforcer, NodeDragEndHandler {

        private WiresConnectionControl connectionControl;

        ConnectionHandler() {
            this.connectionControl = m_wiresManager.getControlFactory().newConnectionControl( m_connector, m_wiresManager );
        }

        @Override
        public void startDrag( final DragContext dragContext ) {
            connectionControl.dragStart( buildContext( dragContext ) );
        }

        @Override
        public boolean adjust( final Point2D dxy ) {
            return connectionControl.dragAdjust( dxy );
        }

        @Override
        public void onNodeDragEnd( final NodeDragEndEvent event ) {
            connectionControl.dragEnd( buildContext( event.getDragContext() ) );
        }
    }

    private WiresDragControlContext buildContext( DragContext context ) {
        return new WiresDragControlContext( context.getDx(), context.getDy(), context.getNode() );
    }

    private final class WiresConnectorControlHandler implements NodeMouseExitHandler, NodeMouseEnterHandler, NodeMouseDoubleClickHandler {

        @Override
        public void onNodeMouseDoubleClick( final NodeMouseDoubleClickEvent event ) {
            WiresConnectorControlImpl.this.destroyControlPoint( event.getSource() );
        }

        @Override
        public void onNodeMouseEnter( final NodeMouseEnterEvent event ) {

            WiresConnectorControlImpl.this.cancelTimer();

            if (((Node<?> ) event.getSource()).getParent() == m_connector.getGroup() && event.isShiftKeyDown())
            {
                WiresConnectorControlImpl.this.showControlPoints();
            }

        }

        @Override
        public void onNodeMouseExit( final NodeMouseExitEvent event ) {

            WiresConnectorControlImpl.this.hideControlPoints();

        }

    }

    private void cancelTimer() {
        if ( m_timer != null ) {
            m_timer.cancel();
            m_timer = null;
        }
    }

    private void createHideTimer() {
        if ( m_timer == null ) {
            m_timer = new Timer() {
                @Override
                public void run() {
                    if ( m_HandlerRegistrationManager != null ) {
                        m_HandlerRegistrationManager.destroy();
                    }
                    m_HandlerRegistrationManager = null;
                    m_connector.getPointHandles().hide();
                }
            };
            m_timer.schedule( 1000 );
        }
    }
}