package at.ac.tuwien.infosys.www.pixy.analysis.dependency.transferfunction;

import at.ac.tuwien.infosys.www.pixy.analysis.AbstractLatticeElement;
import at.ac.tuwien.infosys.www.pixy.analysis.AbstractTransferFunction;
import at.ac.tuwien.infosys.www.pixy.analysis.alias.AliasAnalysis;
import at.ac.tuwien.infosys.www.pixy.analysis.dependency.DependencyLatticeElement;
import at.ac.tuwien.infosys.www.pixy.analysis.dependency.DependencySet;
import at.ac.tuwien.infosys.www.pixy.analysis.interprocedural.AbstractContext;
import at.ac.tuwien.infosys.www.pixy.analysis.interprocedural.AbstractInterproceduralAnalysisNode;
import at.ac.tuwien.infosys.www.pixy.conversion.AbstractTacPlace;
import at.ac.tuwien.infosys.www.pixy.conversion.TacFunction;
import at.ac.tuwien.infosys.www.pixy.conversion.Variable;
import at.ac.tuwien.infosys.www.pixy.conversion.cfgnodes.CallPreparation;

import java.util.*;

/**
 * @author Nenad Jovanovic <enji@seclab.tuwien.ac.at>
 */
public class CallReturn extends AbstractTransferFunction {
    private AbstractInterproceduralAnalysisNode analysisNodeAtCallPrep;
    private TacFunction caller;
    private TacFunction callee;
    private CallPreparation prepNode;
    private at.ac.tuwien.infosys.www.pixy.conversion.cfgnodes.CallReturn retNode;
    private AliasAnalysis aliasAnalysis;

    // contains the set of global-likes that have been modified by the callee
    private Set<AbstractTacPlace> calleeMod;

    // call-by-reference parameter pairs
    private List<List<Variable>> cbrParams;

    // local variables of the calling function
    private Collection<Variable> localCallerVars;

// *********************************************************************************
// CONSTRUCTORS ********************************************************************
// *********************************************************************************

    public CallReturn(
        AbstractInterproceduralAnalysisNode analysisNodeAtCallPrep,
        TacFunction caller,
        TacFunction callee,
        CallPreparation prepNode,
        at.ac.tuwien.infosys.www.pixy.conversion.cfgnodes.CallReturn retNode,
        AliasAnalysis aliasAnalysis,
        Set<AbstractTacPlace> calleeMod) {

        this.analysisNodeAtCallPrep = analysisNodeAtCallPrep;
        this.caller = caller;
        this.callee = callee;

        // call-by-reference parameter pairs
        this.cbrParams = prepNode.getCbrParams();

        // local variables of the calling function
        this.localCallerVars = caller.getLocals();

        this.aliasAnalysis = aliasAnalysis;
        this.calleeMod = calleeMod;

        this.prepNode = prepNode;
        this.retNode = retNode;
    }

// *********************************************************************************
// OTHER ***************************************************************************
// *********************************************************************************

    public AbstractLatticeElement transfer(AbstractLatticeElement inX, AbstractContext context) {

        // lattice element entering the call prep node under the current
        // context
        DependencyLatticeElement origInfo =
            (DependencyLatticeElement) this.analysisNodeAtCallPrep.getPhiValue(context);

        // lattice element coming in from the callee (= base for interprocedural info);
        // still contains the callee's locals
        DependencyLatticeElement calleeIn = (DependencyLatticeElement) inX;

        // start only with default mappings
        DependencyLatticeElement outInfo = new DependencyLatticeElement();

        // contains variables that have been tagged as visited
        Set<Variable> visitedVars = new HashSet<>();

        // copy mappings of "global-like" places from calleeIn to outInfo
        // ("global-like": globals, superglobals, and constants)
        if (this.calleeMod == null) {
            outInfo.copyGlobalLike(calleeIn);
        } else {
            // if we have MOD-info for the callee, use it!
            outInfo.copyGlobalLike(calleeIn, origInfo, this.calleeMod);
        }

        // LOCAL VARIABLES *************

        // no need to do the steps below if the caller is main:
        // its local variables are global variables;
        // instead, do the following:
        if (this.caller.isMain()) {

            // don't forget the main function's temporaries
            outInfo.copyMainTemporaries(origInfo);

            outInfo.handleReturnValue(this.retNode/*, calleeIn*/);

            return outInfo;
        }

        // initialize local variables with the mappings at call-time
        outInfo.copyLocals(origInfo);

        // MUST WITH GLOBALS

        // for all local variables of the calling function
        for (Variable localCallerVar : localCallerVars) {
            // an arbitrary global must-alias of this local
            Variable globalMustAlias = this.aliasAnalysis.getGlobalMustAlias(localCallerVar, this.prepNode);
            if (globalMustAlias == null) {
                continue;
            }

            // the shadow of this global must-alias
            Variable globalMustAliasShadow = this.callee.getSymbolTable().getGShadow(globalMustAlias);
            if (globalMustAliasShadow == null) {
                System.out.println(globalMustAlias + " is global? " + globalMustAlias.isGlobal());
                throw new RuntimeException("SNH: " + globalMustAlias);
            }

            // set & mark
            outInfo.setLocal(
                localCallerVar,
                calleeIn.getDep(globalMustAliasShadow),
                calleeIn.getArrayLabel(globalMustAliasShadow));
            visitedVars.add(localCallerVar);
        }

        // MUST WITH FORMALS

        // for each call-by-reference parameter pair
        for (List<Variable> paramPair : this.cbrParams) {
            Iterator<Variable> paramPairIterator = paramPair.iterator();
            Variable actualVar = paramPairIterator.next();
            Variable formalVar = paramPairIterator.next();

            // local must-aliases of the actual parameter (including trivial ones,
            // so this set contains at least one element)
            for (Variable localMustAlias : this.aliasAnalysis.getLocalMustAliases(actualVar, this.prepNode)) {
                // no need to handle visited variables again
                if (visitedVars.contains(localMustAlias)) {
                    continue;
                }

                // the formal parameter's f-shadow
                Variable fShadow = this.callee.getSymbolTable().getFShadow(formalVar);

                // set & mark
                outInfo.setLocal(
                    localMustAlias,
                    calleeIn.getDep(fShadow),
                    calleeIn.getArrayLabel(fShadow));
                visitedVars.add(localMustAlias);
            }
        }

        // MAY WITH GLOBALS

        // for each local variable that was not visited yet
        for (Variable localCallerVar : localCallerVars) {
            if (visitedVars.contains(localCallerVar)) {
                continue;
            }

            // global may-aliases of this variable (at the time of call)
            Set<Variable> globalMayAliases = this.aliasAnalysis.getGlobalMayAliases(localCallerVar, this.prepNode);

            // if there are no such aliases: nothing to do
            if (globalMayAliases.isEmpty()) {
                continue;
            }

            // initialize this variable's taint/arrayLabel with its original taint/arrayLabel
            DependencySet computedTaint = origInfo.getDep(localCallerVar);
            DependencySet computedArrayLabel = origInfo.getArrayLabel(localCallerVar);

            // for all these global may-aliases...
            for (Variable globalMayAlias : globalMayAliases) {
                 // its g-shadow
                Variable globalMayAliasShadow = this.callee.getSymbolTable().getGShadow(globalMayAlias);

                // the shadow's taint/label (from flowback-info)
                DependencySet shadowTaint = calleeIn.getDep(globalMayAliasShadow);
                DependencySet shadowArrayLabel = calleeIn.getArrayLabel(globalMayAliasShadow);

                // lub
                computedTaint = DependencyLatticeElement.lub(computedTaint, shadowTaint);
                computedArrayLabel = DependencyLatticeElement.lub(computedArrayLabel, shadowArrayLabel);
            }

            // set the local's taint/label to the computed taint/label in outInfo
            outInfo.setLocal(localCallerVar, computedTaint, computedArrayLabel);

            // DON'T mark it as visited
        }

        // MAY WITH FORMALS

        // for each call-by-reference parameter pair
        for (List<Variable> paramPair : this.cbrParams) {
            Iterator<Variable> paramPairIterator = paramPair.iterator();
            Variable actualVar = paramPairIterator.next();
            Variable formalVar = paramPairIterator.next();

            // local may-aliases of the actual parameter (at call-time)
            // for each such may-alias that was not visited yet
            for (Variable localMayAlias : this.aliasAnalysis.getLocalMayAliases(actualVar, this.prepNode)) {
                if (visitedVars.contains(localMayAlias)) {
                    continue;
                }

                // the current taint/label of the local may-alias in output-info
                DependencySet localTaint = outInfo.getDep(localMayAlias);
                DependencySet localArrayLabel = outInfo.getArrayLabel(localMayAlias);

                // the formal parameter's f-shadow
                Variable fShadow = this.callee.getSymbolTable().getFShadow(formalVar);

                // the shadow's taint/label (from flowback-info)
                DependencySet shadowTaint = calleeIn.getDep(fShadow);
                DependencySet shadowArrayLabel = calleeIn.getArrayLabel(fShadow);

                // lub & set
                DependencySet newTaint = DependencyLatticeElement.lub(localTaint, shadowTaint);
                DependencySet newArrayLabel = DependencyLatticeElement.lub(localArrayLabel, shadowArrayLabel);

                outInfo.setLocal(localMayAlias, newTaint, newArrayLabel);
            }
        }

        outInfo.handleReturnValue(this.retNode/*, calleeIn*/);

        return outInfo;
    }

    // just a dummy method in order to make me conform to the interface;
    // the Analysis uses the other transfer method instead
    public AbstractLatticeElement transfer(AbstractLatticeElement inX) {
        throw new RuntimeException("SNH");
    }
}