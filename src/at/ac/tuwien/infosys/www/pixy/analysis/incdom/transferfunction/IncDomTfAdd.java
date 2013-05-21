package at.ac.tuwien.infosys.www.pixy.analysis.incdom.transferfunction;

import at.ac.tuwien.infosys.www.pixy.analysis.LatticeElement;
import at.ac.tuwien.infosys.www.pixy.analysis.TransferFunction;
import at.ac.tuwien.infosys.www.pixy.analysis.incdom.IncDomAnalysis;
import at.ac.tuwien.infosys.www.pixy.analysis.incdom.IncDomLatticeElement;
import at.ac.tuwien.infosys.www.pixy.conversion.cfgnodes.AbstractCfgNode;

/**
 * Transfer function for adding include dominators.
 *
 * @author Nenad Jovanovic <enji@seclab.tuwien.ac.at>
 */
public class IncDomTfAdd extends TransferFunction {
    private AbstractCfgNode cfgNode;
    private IncDomAnalysis incDomAnalysis;

// *********************************************************************************
// CONSTRUCTORS ********************************************************************
// *********************************************************************************

    public IncDomTfAdd(AbstractCfgNode cfgNode, IncDomAnalysis incDomAnalysis) {
        this.cfgNode = cfgNode;
        this.incDomAnalysis = incDomAnalysis;
    }

// *********************************************************************************
// OTHER ***************************************************************************
// *********************************************************************************

    public LatticeElement transfer(LatticeElement inX) {

        IncDomLatticeElement in = (IncDomLatticeElement) inX;
        IncDomLatticeElement out = new IncDomLatticeElement(in);
        out.add(this.cfgNode);

        // recycle
        out = (IncDomLatticeElement) this.incDomAnalysis.recycle(out);

        return out;
    }
}