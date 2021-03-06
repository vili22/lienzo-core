/*
   Copyright (c) 2014,2015,2016 Ahome' Innovation Technologies. All rights reserved.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package com.ait.lienzo.client.core.shape.wires.handlers.impl;

import com.ait.lienzo.client.core.shape.wires.PickerPart;
import com.ait.lienzo.client.core.shape.wires.WiresManager;
import com.ait.lienzo.client.core.shape.wires.WiresShape;
import com.ait.lienzo.client.core.shape.wires.WiresUtils;
import com.ait.lienzo.client.core.shape.wires.handlers.AlignAndDistributeControl;
import com.ait.lienzo.client.core.shape.wires.handlers.WiresDockingAndContainmentControl;
import com.ait.lienzo.client.core.shape.wires.handlers.WiresShapeControl;
import com.ait.lienzo.client.core.types.BoundingBox;
import com.ait.lienzo.client.core.types.Point2D;

/**
 * The DockingAndContainment snap is applied first and thus takes priority. If DockingAndContainment snap is applied, then AlignAndDistribute snap is only applied if the
 * result is still on a point of the path. If the snap would move the point off the path, then the adjust is undone.
 *
 * If DockingAndContainment snap is not applied, then AlignAndDistribute can be applied regardless.
 */
public class WiresShapeControlImpl implements WiresShapeControl
{
    @SuppressWarnings("unused")
    private WiresManager                      m_manager;

    private WiresShape                        m_shape;

    private AlignAndDistributeControl         m_alignAndDistributeHandler;

    private WiresDockingAndContainmentControl m_dockingAndContainmentControl;

    private double                            m_shapeStartX;

    private double                            m_shapeStartY;

    public WiresShapeControlImpl(WiresShape shape, WiresManager wiresManager)
    {
        m_manager = wiresManager;
        m_shape = shape;
    }

    @Override
    public void setAlignAndDistributeControl(AlignAndDistributeControl alignAndDistributeHandler)
    {
        m_alignAndDistributeHandler = alignAndDistributeHandler;
    }

    @Override
    public void setDockingAndContainmentControl(WiresDockingAndContainmentControl m_dockingAndContainmentControl)
    {
        this.m_dockingAndContainmentControl = m_dockingAndContainmentControl;
    }

    @Override
    public void dragStart(final Context context)
    {

        final Point2D absShapeLoc = WiresUtils.getLocation(m_shape.getPath());
        m_shapeStartX = absShapeLoc.getX();
        m_shapeStartY = absShapeLoc.getY();

        if (m_dockingAndContainmentControl != null)
        {
            m_dockingAndContainmentControl.dragStart(context);
        }

        if (m_alignAndDistributeHandler != null)
        {
            m_alignAndDistributeHandler.dragStart();
        }

    }

    @Override
    public void dragEnd(final Context context)
    {

        if (m_dockingAndContainmentControl != null)
        {
            m_dockingAndContainmentControl.dragEnd(context);
        }

        if (m_alignAndDistributeHandler != null)
        {
            m_alignAndDistributeHandler.dragEnd();
        }

    }

    @Override
    public void dragMove(final Context context)
    {
        // Nothing to do.
    }

    @Override
    public boolean dragAdjust(final Point2D dxy)
    {

        boolean adjusted1 = false;
        if (m_dockingAndContainmentControl != null)
        {
            adjusted1 = m_dockingAndContainmentControl.dragAdjust(dxy);
        }

        double dx = dxy.getX();
        double dy = dxy.getY();
        boolean adjusted2 = false;
        if (m_alignAndDistributeHandler != null && m_alignAndDistributeHandler.isDraggable())
        {
            adjusted2 = m_alignAndDistributeHandler.dragAdjust(dxy);
        }

        if (adjusted1 && adjusted2 && (dxy.getX() != dx || dxy.getY() != dy))
        {
            BoundingBox box = m_shape.getPath().getBoundingBox();

            PickerPart part = m_dockingAndContainmentControl.getPicker().findShapeAt((int) (m_shapeStartX + dxy.getX() + (box.getWidth() / 2)), (int) (m_shapeStartY + dxy.getY() + (box.getHeight() / 2)));

            if (part == null || part.getShapePart() != PickerPart.ShapePart.BORDER)
            {
                dxy.setX(dx);
                dxy.setY(dy);
                adjusted2 = false;
            }
        }

        return adjusted1 || adjusted2;

    }

    @Override
    public void onNodeMouseDown()
    {
        if (m_dockingAndContainmentControl != null)
        {
            m_dockingAndContainmentControl.onNodeMouseDown();
        }
    }

    @Override
    public void onNodeMouseUp()
    {
        if (m_dockingAndContainmentControl != null)
        {
            m_dockingAndContainmentControl.onNodeMouseUp();
        }
    }

}
