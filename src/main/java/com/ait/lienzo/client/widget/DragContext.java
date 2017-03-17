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

package com.ait.lienzo.client.widget;

import com.ait.lienzo.client.core.Context2D;
import com.ait.lienzo.client.core.event.INodeXYEvent;
import com.ait.lienzo.client.core.shape.Attributes;
import com.ait.lienzo.client.core.shape.IPrimitive;
import com.ait.lienzo.client.core.shape.Node;
import com.ait.lienzo.client.core.types.Point2D;
import com.ait.lienzo.client.core.types.Transform;
import com.ait.lienzo.shared.core.types.NodeType;

/**
 * DragContext maintains information during a Drag operation of a Node.
 * <p>
 * Some values are defined in "global coordinates" (a.k.a. "canvas coordinates"
 * or "viewport coordinates"), 
 * e.g. the raw canvas event (x,y) values are defined in pixels relative to the top-left corner of the canvas.
 * <p> 
 * On the other hand, "local coordinates" are specific to a node's parent.
 * E.g. the X,Y position of a node is defined relative to the top-left corner of the parent node.
 * <p>
 * To convert between local and global coordinates, you'd need to concatenate the Transform
 * of the nodes ancestors, all the way down to the parent node (see {@link #getLocalToGlobal()}).
 * Converting from global to local coordinates requires the inverse of that Transform 
 * (see {@link #getGlobalToLocal()}.)
 */
public class DragContext
{
    private int                          m_evtx;

    private int                          m_evty;

    private int                          m_dstx;

    private int                          m_dsty;

    private double                       m_lstx;

    private double                       m_lsty;

    private final Transform              m_gtol;

    private final Transform              m_ltog;

    private final IPrimitive<?>          m_prim;

    private final double                 m_prmx;

    private final double                 m_prmy;

    private final int                    m_begx;

    private final int                    m_begy;

    private final DragConstraintEnforcer m_drag;

    private final Point2D                m_lclp = new Point2D(0, 0);

    private final Point2D                m_pref = new Point2D(0, 0);

    /**
     * Starts a drag operation for the specified node.
     * 
     * @param event the first drag event
     * @param prim the node that is being dragged
     */
    public DragContext(final INodeXYEvent event, final IPrimitive<?> prim)
    {
        m_prim = prim;

        m_lstx = m_prmx = m_prim.getX();

        m_lsty = m_prmy = m_prim.getY();

        m_evtx = m_begx = event.getX();

        m_evty = m_begy = event.getY();

        m_ltog = m_prim.getParent().getAbsoluteTransform();

        m_gtol = m_ltog.getInverse();

        // Convert one point from global to local coordinates
        // We need it when calculating (dx,dy) in local coordinates

        m_gtol.transform(new Point2D(0, 0), m_pref);

        // Initialize the DragConstraintsEnforcer

        m_drag = m_prim.getDragConstraints();

        if (m_drag != null)
        {
            m_drag.startDrag(this);
        }
    }

    /**
     * Draws the node during a drag operation.
     * Used internally.
     * 
     * @param context
     */
    public void drawNodeWithTransforms(final Context2D context)
    {
        context.save();

        context.transform(m_ltog);

        m_prim.drawWithTransforms(context, getNodeParentsAlpha(m_prim.asNode()), null);

        context.restore();
    }

    /**
     * Returns global alpha value.
     * 
     * @return double
     */
    private final double getNodeParentsAlpha(Node<?> node)
    {
        double alpha = 1;

        node = node.getParent();

        while (null != node)
        {
            alpha = alpha * node.getAttributes().getAlpha();

            node = node.getParent();

            if ((null != node) && (node.getNodeType() == NodeType.LAYER))
            {
                node = null;
            }
        }
        return alpha;
    }

    /**
     * Updates the context for the specified Drag Move event.
     * Used internally.
     * 
     * @param event Drag Move event
     */
    public void dragUpdate(final INodeXYEvent event)
    {
        m_evtx = event.getX();

        m_evty = event.getY();

        m_dstx = m_evtx - m_begx;

        m_dsty = m_evty - m_begy;

        final Point2D p2 = new Point2D(0, 0);

        m_gtol.transform(new Point2D(m_dstx, m_dsty), p2);

        m_lclp.setX(p2.getX() - m_pref.getX());

        m_lclp.setY(p2.getY() - m_pref.getY());

        // Let the constraints adjust the location if necessary

        if (m_drag != null)
        {
            m_drag.adjust(m_lclp);
        }
        
        Point2D localDrag = convertDragToLocalCoordinates(m_lclp); 
        final double x = m_prmx + localDrag.getX();
        final double y = m_prmy + localDrag.getY();
        
        if (m_lstx != x)
        {
            m_prim.setX(m_lstx = x);
        }
        if (m_lsty != y)
        {
            m_prim.setY(m_lsty = y);
        }
    }

    /**
     * Called when the Drag operation is done.
     * It basically updates the node's new (X,Y) attributes.
     * 
     * Used internally.
     */
    public void dragDone()
    {
    	
    	Point2D localDrag = convertDragToLocalCoordinates(m_lclp); 
        final double x = m_prmx + localDrag.getX();
        final double y = m_prmy + localDrag.getY();
        
        if (m_lstx != x)
        {
            m_prim.setX(m_lstx = x);
        }
        if (m_lsty != y)
        {
            m_prim.setY(m_lsty = y);
        }
    }

    /**
     * Moves the Node back to where it was before the drag operation.
     * Use this to undo the drag.
     */
    public void reset()
    {
        m_prim.setX(m_prmx);

        m_prim.setY(m_prmy);
    }

    /**
     * Returns x0 in global coordinates - i.e. event(x,y) at start of drag operation
     * 
     * @return int
     */
    public int getDragStartX()
    {
        return m_begx;
    }

    /**
     * Returns y0 in global coordinates - i.e. event(x,y) at start of drag operation
     * 
     * @return int
     */
    public int getDragStartY()
    {
        return m_begy;
    }

    /**
     * Returns x1 in global coordinates - i.e. event(x,y) of last drag move
     * @return
     */
    public int getEventX()
    {
        return m_evtx;
    }

    /**
     * Returns y1 in global coordinates - i.e. event(x,y) of last drag move
     * @return
     */
    public int getEventY()
    {
        return m_evty;
    }

    /**
     * Returns dx (i.e. eventX - dragstartX) in global coordinates
     * 
     * @return int
     */
    public int getDx()
    {
        return m_dstx;
    }

    /**
     * Returns dy (i.e. eventY - dragStartY) in global coordinates
     * 
     * @return int
     */
    public int getDy()
    {
        return m_dsty;
    }

    /**
     * Returns the Transform that can be used to convert global
     * coordinates (i.e. canvas coordinates) to local coordinates
     * (i.e. within the context of the node's parent.)
     * This is the inverse of the localToGlobal transform.
     * 
     * @return
     */
    public Transform getGlobalToLocal()
    {
        return m_gtol;
    }

    /**
     * Returns the Transform that can be used to convert local coordinates
     * (i.e. within the context of the node's parent) to global
     * coordinates (i.e. canvas coordinates).
     * This is the inverse of the globalToLocal transform.
     * 
     * @return
     */
    public Transform getLocalToGlobal()
    {
        return m_ltog;
    }

    /**
     * Returns (dx,dy) in local coordinates, adjusted by the 
     * {@link DragConstraintEnforcer}
     * 
     * @return Point2D
     */
    public Point2D getLocalAdjusted()
    {
        return m_lclp;
    }

    /**
     * Returns the node being dragged as an IPrimitive.
     * 
     * @return IPrimitive
     */
    public IPrimitive<?> getNode()
    {
        return m_prim;
    }

    /**
     * Returns the {@link DragConstraintEnforcer} that adjusts the node
     * location during a drag operation.
     * 
     * @return {@link DragConstraintEnforcer}
     */
    public DragConstraintEnforcer getDragConstraints()
    {
        return m_drag;
    }
    
    public static Transform getPossibleNodeTransform(Attributes attributes) {

		if (!attributes.hasAnyTransformAttributes()) {
			return null;
		}

		final Transform xfrm = new Transform();

		if (!attributes.hasComplexTransformAttributes()) {
			xfrm.translate(attributes.getX(), attributes.getY());
			return xfrm;
		}
		// Otherwise use ROTATION, SCALE, OFFSET and SHEAR

		double ox = 0;

		double oy = 0;

		final Point2D offset = attributes.getOffset();

		if (null != offset) {
			ox = offset.getX();

			oy = offset.getY();
		}
		final double r = attributes.getRotation();

		if (r != 0) {
			if ((ox != 0) || (oy != 0)) {
				xfrm.translate(ox, oy);

				xfrm.rotate(r);

				xfrm.translate(-ox, -oy);
			} else {
				xfrm.rotate(r);
			}
		}
		final Point2D scale = attributes.getScale();

		if (null != scale) {
			final double sx = scale.getX();

			final double sy = scale.getY();

			if ((sx != 1) || (sy != 1)) {
				if ((ox != 0) || (oy != 0)) {
					xfrm.translate(ox, oy);

					xfrm.scale(sx, sy);

					xfrm.translate(-ox, -oy);
				} else {
					xfrm.scale(sx, sy);
				}
			}
		}
		final Point2D shear = attributes.getShear();

		if (null != shear) {
			final double sx = shear.getX();

			final double sy = shear.getY();

			if ((sx != 0) || (sy != 0)) {
				xfrm.shear(sx, sy);
			}
		}

		xfrm.translate(attributes.getX(), attributes.getY());
		return xfrm;
	}
    
    public Point2D convertDragToLocalCoordinates(Point2D drag) {
    	
    	Point2D transFormedDrag = new Point2D(0,0);
    	Point2D localConstantShift = new Point2D(0,0);
    	
    	Transform localTransform = getPossibleNodeTransform(m_prim.asNode().getAttributes());
    	localTransform.getInverse().transform(m_lclp, transFormedDrag);
    	localTransform.getInverse().transform(localConstantShift, localConstantShift);
    	
    	return new Point2D(transFormedDrag.getX() - localConstantShift.getX(), transFormedDrag.getY() - localConstantShift.getY());   	
    }
}