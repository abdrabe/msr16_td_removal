// $Header$
/*
 * Copyright 2004 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jmeter.visualizers;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Iterator;
import java.util.List;

import javax.swing.JComponent;

import org.apache.jmeter.gui.util.JMeterColor;
import org.apache.jmeter.samplers.Clearable;

public class MonitorGraph
    extends JComponent
    implements MouseListener, MonitorGuiListener, Clearable
{
	private static int width = 500;
	private MonitorAccumModel MODEL;
	private MonitorModel CURRENT;
	
	private boolean CPU = false;
	private boolean HEALTH = true;
	private boolean LOAD = true;
	private boolean MEM = true;
	private boolean THREAD = true;
	private boolean YGRID = true;
	private boolean XGRID = true;

	private int COUNT = 0;
	private int GRAPHMAX = 0;
    /**
     * 
     */
    public MonitorGraph(MonitorAccumModel model)
    {
        this.MODEL = model;
        GRAPHMAX = model.getBufferSize();
        init();
    }

	private void init(){
		repaint();
	}
	
	public void setCpu(boolean cpu){
		this.CPU = cpu;
	}
	
	public void setHealth(boolean health){
		this.HEALTH = health;
	}
	
	public void setLoad(boolean load){
		this.LOAD = load;
	}
	
	public void setMem(boolean mem){
		this.MEM = mem;
	}
	
	public void setThread(boolean thread){
		this.THREAD = thread;
	}
	
    /* (non-Javadoc)
     * @see java.awt.event.MouseListener#mouseClicked(java.awt.event.MouseEvent)
     */
    public void mouseClicked(MouseEvent e)
    {
    }

    /* (non-Javadoc)
     * @see java.awt.event.MouseListener#mouseEntered(java.awt.event.MouseEvent)
     */
    public void mouseEntered(MouseEvent e)
    {
    }

    /* (non-Javadoc)
     * @see java.awt.event.MouseListener#mouseExited(java.awt.event.MouseEvent)
     */
    public void mouseExited(MouseEvent e)
    {
    }

    /* (non-Javadoc)
     * @see java.awt.event.MouseListener#mousePressed(java.awt.event.MouseEvent)
     */
    public void mousePressed(MouseEvent e)
    {
    }

    /* (non-Javadoc)
     * @see java.awt.event.MouseListener#mouseReleased(java.awt.event.MouseEvent)
     */
    public void mouseReleased(MouseEvent e)
    {

    }

    /* (non-Javadoc)
     * @see org.apache.jmeter.visualizers.MonitorGuiListener#updateGui(org.apache.jmeter.visualizers.MonitorModel)
     */
    public void updateGui(final MonitorModel model)
    {
    	if (this.isShowing()){
			this.CURRENT = model;
			repaint();
    	}
    }

	public void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		if (this.CURRENT != null){
			synchronized(MODEL){
				List samples = MODEL.getAllSamples(this.CURRENT.getURL());
				int size = samples.size();
				synchronized (samples)
				{
					Iterator e;
					if (size > getWidth()){
						e = samples.listIterator(size - getWidth());
					} else {
						e = samples.iterator();
					}
					MonitorModel last = null;
					for (int i = 0; e.hasNext(); i++)
					{
						MonitorModel s = (MonitorModel) e.next();
						if (last == null){
							last = s;
						}
						drawSample(i, s, g, last);
						last = s;
					}
				}
			}
		}
	}

    /* (non-Javadoc)
     * @see org.apache.jmeter.visualizers.MonitorGuiListener#updateGui()
     */
    public void updateGui()
    {
    	repaint();
    }

    /* (non-Javadoc)
     * @see org.apache.jmeter.samplers.Clearable#clear()
     */
    public void clear()
    {
    	paintComponent(getGraphics());
    	this.repaint();
    }
    
	private void drawSample(int x, MonitorModel model,
		Graphics g, MonitorModel last)
	{
		double width = (double)getWidth();
		double height = (double)getHeight() - 10.0;
		// int xaxis = (int)(x % width);
		// int lastx = (int)((x - 1) % width);
		int xaxis = (int)(width * (x /width));
		int lastx = (int)(width * ((x - 1)/width));
		
		// draw grid
		if (YGRID && x == 1){
			int q1 = (int)(height * 0.25);
			int q2 = (int)(height * 0.50);
			int q3 = (int)(height * 0.75);
			g.setColor(JMeterColor.lightGray);
			g.drawLine(0,q1,getWidth(),q1);
			g.drawLine(0,q2,getWidth(),q2);
			g.drawLine(0,q3,getWidth(),q3);
		}
		if (XGRID && x == 1){
			int x1 = (int)(width * 0.25);
			int x2 = (int)(width * 0.50);
			int x3 = (int)(width * 0.75);
			g.drawLine(x1,0,x1,getHeight());
			g.drawLine(x2,0,x2,getHeight());
			g.drawLine(x3,0,x3,getHeight());
			g.drawLine(getWidth(),0,getWidth(),getHeight());
		}
		
		if (HEALTH)
		{
			int hly =
				(int)(height - (height * ((double)model.getHealth()/3.0)));
			int lasty =
				(int)(height - (height * ((double)last.getHealth()/3.0)));

			g.setColor(JMeterColor.GREEN);
			g.drawLine(lastx,lasty,xaxis,hly);
		}

		if (LOAD)
		{
			int ldy =
				(int)(height - (height * ((double)model.getLoad()/100.0)));
			int lastldy =
				(int)(height - (height * ((double)last.getLoad()/100.0)));

			g.setColor(Color.BLUE);
			g.drawLine(lastx,lastldy,xaxis,ldy);
		}

		if (MEM)
		{
			int mmy =
				(int)(height - (height * ((double)model.getMemload()/100.0)));
			int lastmmy =
				(int)(height - (height * ((double)last.getMemload()/100.0)));

			g.setColor(JMeterColor.ORANGE);
			g.drawLine(lastx,lastmmy,xaxis,mmy);
		}

		if (THREAD)
		{
			int thy =
				(int)(height - (height * ((double)model.getThreadload()/100.0)));
			int lastthy =
				(int)(height - (height * ((double)last.getThreadload()/100.0)));

			g.setColor(Color.RED);
			g.drawLine(lastx,lastthy,xaxis,thy);
		}
	}

}
