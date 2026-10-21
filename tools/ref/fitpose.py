import sys, json, numpy as np
sys.path.insert(0,'/home/claude/battlegrounds-mod/tools')
import preview as P
from geolib import rot_xyz, T, M4
from scipy.optimize import least_squares
S='/tmp/claude-0/-home-claude/cb9e77f1-b529-5428-b56f-ba9e74153286/scratchpad'
geo,_=P.load('m416')
W0=geo.world(None)
HIDE={'righthand','lefthand','camera','idle_view','iron_view'}
pts=[]
for name in geo.order:
    if name in HIDE: continue
    for c in geo.bones[name]['cubes']:
        M=W0[name]
        if 'r' in c:
            o=c.get('o',[0,0,0]); M=M@T(o)@M4(rot_xyz(*c['r']))@T([-v for v in o])
        f,t=c['f'],c['t']
        for x in (f[0],t[0]):
            for y in (f[1],t[1]):
                for z in (f[2],t[2]):
                    pts.append((M@np.array([x,y,z,1.0]))[:3])
PTS=np.array(pts)
PIV=np.array(geo.bones['root']['pivot'],float)
CAM=geo.locator('idle_view')[:3,3]
FOC=0.5/np.tan(np.radians(35))
BORE=np.array([[0,9.5,-20.5],[0,9.5,0.0]])
def proj(P3):
    c=P3-CAM; z=-c[:,2]
    return np.column_stack([FOC*c[:,0]/z, -FOC*c[:,1]/z])
def measure(rot,pos):
    R=rot_xyz(*rot)
    W=PIV+pos+(PTS-PIV)@R.T
    B=PIV+pos+(BORE-PIV)@R.T
    b=proj(B); d=b[0]-b[1]; d/=np.linalg.norm(d)
    q=proj(W); tip=q[np.argmax(q@d)]
    ang=np.degrees(np.arctan2(-d[1],-d[0]))
    return tip,ang,R
def solve(tip_ref,ang_ref,top_hint,mz_hint,pz_prior,x0,w_top=0.6,w_mz=0.6):
    top_hint=np.array(top_hint,float); top_hint/=np.linalg.norm(top_hint)
    def res(p):
        rot,pos=p[:3],p[3:]
        tip,ang,R=measure(rot,pos)
        top=R@[0,1,0]; m=R@[0,0,-1]
        return np.concatenate([(tip-tip_ref)*540,[(ang-ang_ref)*3],
            (top-top_hint)*40*w_top,[(m[2]-mz_hint)*40*w_mz],[(pos[2]-pz_prior)*1.5],(p-x0)*0.05])
    r=least_squares(res,x0,x_scale=[10,10,10,1,1,1])
    return r.x

def measure2(rot,pos,below=30/518):
    R=rot_xyz(*rot)
    W=PIV+pos+(PTS-PIV)@R.T
    B=PIV+pos+(BORE-PIV)@R.T
    b=proj(B); d=b[0]-b[1]; d/=np.linalg.norm(d)
    q=proj(W); tip=q[np.argmax(q@d)]
    n=np.array([d[1],-d[0]]); n=-n if n[1]<0 else n
    along=(tip-q)@d; perp=(q-tip)@n
    sel=(perp>below)&(along>0)
    mag=along[sel].min() if sel.any() else 0.0
    return tip,np.degrees(np.arctan2(-d[1],-d[0])),R,mag
def solve2(tip_ref,ang_ref,mag_ref,top_hint,x0,w_top=0.5,pz_prior=None,pzw=1.5):
    top_hint=np.array(top_hint,float); top_hint/=np.linalg.norm(top_hint)
    def res(p):
        tip,ang,R,mag=measure2(p[:3],p[3:])
        top=R@[0,1,0]
        r=[(tip-tip_ref)*518,[(ang-ang_ref)*3],(top-top_hint)*40*w_top,(p-x0)*0.05]
        if mag_ref is not None: r.append([(mag-mag_ref)*518])
        if pz_prior is not None: r.append([(p[5]-pz_prior)*pzw])
        return np.concatenate(r)
    return least_squares(res,x0,x_scale=[10,10,10,1,1,1]).x

FEAT={'B':np.array([0,5.2,-3.6]),'C':np.array([0,13.1,5.4])}
def nrm(p): return np.array([(p[0]-480)/518,(p[1]-281)/518])
def solve3(tip_ref,ang_ref,feats,x0,top_hint=None,w_top=0.0,pz_prior=None,pzw=0.0,mz_hint=None,w_mz=0.0):
    def res(p):
        tip,ang,R,_=measure2(p[:3],p[3:])
        r=[(tip-tip_ref)*518,[(ang-ang_ref)*3],(p-x0)*0.02]
        for k,(xy,w) in feats.items():
            q=proj((PIV+p[3:]+R@(FEAT[k]-PIV))[None])[0]
            r.append((q-nrm(xy))*518*w)
        if top_hint is not None:
            th=np.array(top_hint,float); th/=np.linalg.norm(th); r.append((R@[0,1,0]-th)*40*w_top)
        if mz_hint is not None: r.append([((R@[0,0,-1])[2]-mz_hint)*40*w_mz])
        if pz_prior is not None: r.append([(p[5]-pz_prior)*pzw])
        return np.concatenate(r)
    return least_squares(res,x0,x_scale=[10,10,10,1,1,1]).x
