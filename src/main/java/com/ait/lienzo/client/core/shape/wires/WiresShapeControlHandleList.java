/*
 *
 *    Copyright (c) 2014,2015,2016 Ahome' Innovation Technologies. All rights reserved.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */
// TODO - review DSJ

package com.ait.lienzo.client.core.shape.wires;

import java.util.Iterator;

import com.ait.lienzo.client.core.event.AbstractNodeDragEvent;
import com.ait.lienzo.client.core.event.NodeDragEndEvent;
import com.ait.lienzo.client.core.event.NodeDragEndHandler;
import com.ait.lienzo.client.core.event.NodeDragMoveEvent;
import com.ait.lienzo.client.core.event.NodeDragMoveHandler;
import com.ait.lienzo.client.core.event.NodeDragStartEvent;
import com.ait.lienzo.client.core.event.NodeDragStartHandler;
import com.ait.lienzo.client.core.shape.Group;
import com.ait.lienzo.client.core.shape.IPrimitive;
import com.ait.lienzo.client.core.shape.MultiPath;
import com.ait.lienzo.client.core.shape.wires.event.WiresDragEndEvent;
import com.ait.lienzo.client.core.shape.wires.event.WiresDragEndHandler;
import com.ait.lienzo.client.core.shape.wires.event.WiresDragMoveEvent;
import com.ait.lienzo.client.core.shape.wires.event.WiresDragMoveHandler;
import com.ait.lienzo.client.core.shape.wires.event.WiresDragStartEvent;
import com.ait.lienzo.client.core.shape.wires.event.WiresDragStartHandler;
import com.ait.lienzo.client.core.shape.wires.event.WiresMoveEvent;
import com.ait.lienzo.client.core.shape.wires.event.WiresMoveHandler;
import com.ait.lienzo.client.core.shape.wires.event.WiresResizeEndEvent;
import com.ait.lienzo.client.core.shape.wires.event.WiresResizeStartEvent;
import com.ait.lienzo.client.core.shape.wires.event.WiresResizeStepEvent;
import com.ait.lienzo.client.core.types.BoundingBox;
import com.ait.lienzo.client.core.types.Point2D;
import com.ait.lienzo.client.core.types.Point2DArray;
import com.ait.tooling.nativetools.client.event.HandlerRegistrationManager;

/**
 * This class handles the Wires Shape controls to provide additional features.
 * As the shape's MultiPath, when resizing using the resize control points, does not updates any attribute, the way
 * the resize is captured when user drags a resize control point is by adding drag handlers to the resize controls. That
 * way it can be calculated the bounding box location and size for the multipath.
 *
 * Future thoughts: if the different parts of the multipath are stored as attributes, another approach based on attributes
 * changed batcher could be used.
 */
public class WiresShapeControlHandleList implements IControlHandleList
{
    private static final int POINTS_SIZE = 4;

    private final WiresShape                       m_wires_shape;

    private final ControlHandleList                m_ctrls;

    private final IControlHandle.ControlHandleType m_ctrls_type;

    private final HandlerRegistrationManager       m_registrationManager;

    private Group                                  parent;

    public WiresShapeControlHandleList(final WiresShape wiresShape, final IControlHandle.ControlHandleType controlsType, final ControlHandleList controls)
    {
        this(wiresShape, controlsType, controls, new HandlerRegistrationManager());
    }

    WiresShapeControlHandleList(final WiresShape wiresShape, final IControlHandle.ControlHandleType controlsType, final ControlHandleList controls, final HandlerRegistrationManager registrationManager)
    {
        this.m_wires_shape = wiresShape;
        this.m_ctrls = controls;
        this.m_ctrls_type = controlsType;
        this.m_registrationManager = registrationManager;
        this.parent = null;
        updateParentLocation();
        initControlsListeners();
    }

    @Override
    public void show()
    {
        switchVisibility(true);
    }

    void refresh()
    {
        final BoundingBox bb = getPath().getBoundingBox();
        final double width = bb.getWidth();
        final double height = bb.getHeight();
        this.resize(null, null, width, height, true);
    }

    @Override
    public void destroy()
    {
        m_ctrls.destroy();
        m_registrationManager.removeHandler();
        if (null != parent)
        {
            parent.removeFromParent();
        }
    }

    @Override
    public int size()
    {
        return m_ctrls.size();
    }

    @Override
    public boolean isEmpty()
    {
        return m_ctrls.isEmpty();
    }

    @Override
    public IControlHandle getHandle(int index)
    {
        return m_ctrls.getHandle(index);
    }

    @Override
    public void add(IControlHandle handle)
    {
        m_ctrls.add(handle);
    }

    @Override
    public void remove(IControlHandle handle)
    {
        m_ctrls.remove(handle);
    }

    @Override
    public boolean contains(IControlHandle handle)
    {
        return m_ctrls.contains(handle);
    }

    @Override
    public void hide()
    {
        switchVisibility(false);
    }

    @Override
    public boolean isVisible()
    {
        return m_ctrls.isVisible();
    }

    @Override
    public HandlerRegistrationManager getHandlerRegistrationManager()
    {
        return m_ctrls.getHandlerRegistrationManager();
    }

    @Override
    public boolean isActive()
    {
        return m_ctrls.isActive();
    }

    @Override
    public boolean setActive(boolean b)
    {
        return m_ctrls.setActive(b);
    }

    @Override
    public Iterator<IControlHandle> iterator()
    {
        return m_ctrls.iterator();
    }

    private void initControlsListeners()
    {
        // Control points - to provide the resize support.
        if (IControlHandle.ControlHandleStandardType.RESIZE.equals(m_ctrls_type))
        {
            for (int i = 0; i < POINTS_SIZE; i++)
            {
                final IControlHandle handle = m_ctrls.getHandle(i);
                final IPrimitive<?> control = handle.getControl();

                m_registrationManager.register(control.addNodeDragStartHandler(new NodeDragStartHandler()
                {
                    @Override
                    public void onNodeDragStart(final NodeDragStartEvent event)
                    {
                        WiresShapeControlHandleList.this.resizeStart(event);
                    }
                }));

                m_registrationManager.register(control.addNodeDragMoveHandler(new NodeDragMoveHandler()
                {
                    @Override
                    public void onNodeDragMove(final NodeDragMoveEvent event)
                    {
                        WiresShapeControlHandleList.this.resizeMove(event);
                    }
                }));

                m_registrationManager.register(control.addNodeDragEndHandler(new NodeDragEndHandler()
                {
                    @Override
                    public void onNodeDragEnd(final NodeDragEndEvent event)
                    {
                        WiresShapeControlHandleList.this.resizeEnd(event);
                    }
                }));
            }
        }

        // Shape container's drag.
        m_registrationManager.register(m_wires_shape.addWiresDragStartHandler(new WiresDragStartHandler()
        {
            @Override
            public void onShapeDragStart(WiresDragStartEvent event)
            {
                updateParentLocation();
            }
        }));

        m_registrationManager.register(m_wires_shape.addWiresDragMoveHandler(new WiresDragMoveHandler()
        {
            @Override
            public void onShapeDragMove(WiresDragMoveEvent event)
            {
                updateParentLocation();
            }
        }));

        m_registrationManager.register(m_wires_shape.addWiresDragEndHandler(new WiresDragEndHandler()
        {
            @Override
            public void onShapeDragEnd(WiresDragEndEvent event)
            {
                updateParentLocation();
            }
        }));

        // Shape container's position.
        m_registrationManager.register(m_wires_shape.addWiresMoveHandler(new WiresMoveHandler()
        {
            @Override
            public void onShapeMoved(WiresMoveEvent event)
            {
                updateParentLocation();
            }
        }));
    }

    protected void resizeStart(final AbstractNodeDragEvent<?> dragEvent)
    {
        final double[] r = this.resizeWhileDrag(dragEvent);

        if (m_wires_shape.isResizable())
        {
            m_wires_shape.getHandlerManager().fireEvent(new WiresResizeStartEvent(m_wires_shape, dragEvent, (int) r[0], (int) r[1], r[2], r[3]));
        }
    }

    protected void resizeMove(final AbstractNodeDragEvent<?> dragEvent)
    {
        final double[] r = this.resizeWhileDrag(dragEvent);

        if (m_wires_shape.isResizable())
        {
            m_wires_shape.getHandlerManager().fireEvent(new WiresResizeStepEvent(m_wires_shape, dragEvent, (int) r[0], (int) r[1], r[2], r[3]));
        }
    }

    protected void resizeEnd(final AbstractNodeDragEvent<?> dragEvent)
    {
        final double[] r = this.resizeWhileDrag(dragEvent);

        if (m_wires_shape.isResizable())
        {
            m_wires_shape.getHandlerManager().fireEvent(new WiresResizeEndEvent(m_wires_shape, dragEvent, (int) r[0], (int) r[1], r[2], r[3]));
        }
    }

    private double[] resizeWhileDrag(final AbstractNodeDragEvent<?> dragEvent)
    {
        if (m_wires_shape.isResizable())
        {
            // Ensure magnets hidden while resizing.
            if (null != m_wires_shape.getMagnets())
            {
                m_wires_shape.getMagnets().hide();
            }

            final Point2DArray points = getControlPointsArray();

            final double[] attrs = getBBAttributes(points);

            this.resize(attrs[0], attrs[1], attrs[2], attrs[3], false);

            return attrs;
        }

        return null;
    }

    protected void resize(final Double x, final Double y, final double width, final double height, final boolean refresh)
    {
        if (null != x && null != y)
        {
            m_wires_shape.getLayoutContainer().setOffset(new Point2D(x, y));
        }

        m_wires_shape.getLayoutContainer().setSize(width, height);

        if (refresh)
        {
            m_wires_shape.getLayoutContainer().refresh();
        }

        m_wires_shape.getLayoutContainer().execute();

        if (null != m_wires_shape.getMagnets())
        {
            m_wires_shape.getMagnets().shapeChanged();
        }

        // For now, move path to bottom to make controls and magnets visible.
        m_wires_shape.getPath().moveToBottom();

        // Layout content whilst resizing
        m_wires_shape.getLayoutHandler().requestLayout( m_wires_shape );
    }

    private Point2DArray getControlPointsArray()
    {
        Point2DArray result = new Point2DArray();

        for (int i = 0; i < POINTS_SIZE; i++)
        {
            final IControlHandle handle = m_ctrls.getHandle(i);
            final IPrimitive<?> control = handle.getControl();
            final Point2D p = new Point2D(control.getX(), control.getY());
            result.push(p);
        }

        return result;
    }

    protected void updateParentLocation()
    {
        if (null == parent && null != getGroup().getLayer())
        {
            this.parent = new Group();
            getGroup().getLayer().add(parent);
        }

        if (null == parent)
        {
            return;
        }

        final double[] ap = getAbsolute(getGroup());
        parent.setX(ap[0]);
        parent.setY(ap[1]);
        parent.moveToTop();

        for (final WiresShape child : m_wires_shape.getChildShapes())
        {
            if (null != child.getControls())
            {
                child.getControls().updateParentLocation();
            }
        }
    }

    private double[] getBBAttributes(final Point2DArray controlPoints)
    {
        double minx = controlPoints.get(0).getX();
        double miny = controlPoints.get(0).getY();
        double maxx = controlPoints.get(0).getX();
        double maxy = controlPoints.get(0).getY();

        for (Point2D control : controlPoints)
        {
            if (control.getX() < minx)
            {
                minx = control.getX();
            }
            if (control.getX() > maxx)
            {
                maxx = control.getX();
            }
            if (control.getY() < miny)
            {
                miny = control.getY();
            }
            if (control.getY() > maxy)
            {
                maxy = control.getY();
            }
        }

        // Resize the primitives container.
        final double w = maxx - minx;
        final double h = maxy - miny;

        return new double[] { minx, miny, w, h };
    }

    private void switchVisibility(final boolean visible)
    {
        if (null == parent)
        {
            return;
        }

        for (WiresShape shape : m_wires_shape.getChildShapes())
        {
            if (shape.getControls() == null)
            {
                continue;
            }

            if (visible)
            {
                shape.getControls().show();
            }
            else
            {
                shape.getControls().hide();
            }
        }

        if (visible)
        {
            m_ctrls.showOn(parent);
        }
        else
        {
            m_ctrls.hide();
        }
    }

    private static double[] getAbsolute(final Group group)
    {
        final Point2D p = WiresUtils.getLocation(group);
        return new double[] { p.getX(), p.getY() };
    }

    private MultiPath getPath()
    {
        return m_wires_shape.getPath();
    }

    private Group getGroup()
    {
        return m_wires_shape.getGroup();
    }
}
